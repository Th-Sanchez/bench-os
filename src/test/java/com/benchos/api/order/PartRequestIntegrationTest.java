package com.benchos.api.order;

import com.benchos.api.TestcontainersConfiguration;
import com.benchos.api.shared.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PartRequestIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        cleanDatabase();
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    private void cleanDatabase() {
        TenantContext.clear();
        jdbcTemplate.execute("DELETE FROM event_publication");
        jdbcTemplate.execute("DELETE FROM financial_transactions");
        jdbcTemplate.execute("DELETE FROM part_requests");
        jdbcTemplate.execute("DELETE FROM service_order_items");
        jdbcTemplate.execute("DELETE FROM service_orders");
        jdbcTemplate.execute("DELETE FROM parts_catalog");
        jdbcTemplate.execute("DELETE FROM tenant_sequences");
        jdbcTemplate.execute("DELETE FROM customers");
        jdbcTemplate.execute("DELETE FROM users");
        jdbcTemplate.execute("DELETE FROM tenant_settings");
        jdbcTemplate.execute("DELETE FROM tenants");
    }

    @Test
    void managesOnDemandPartRequestsWithRbacTenantIsolationAutoTransitionAndFinanceExpense() throws Exception {
        RegisteredShop shopA = registerShop("Oficina Pecas A", "11222333000181", "11999990001", "Dono A", "a@partsreq.com");
        RegisteredShop shopB = registerShop("Oficina Pecas B", "44555666000192", "11999990002", "Dono B", "b@partsreq.com");
        String attendantToken = createStaffAndLogin(shopA.token(), "Atendente A", "att@partsreq.com", "ATTENDANT");
        String techToken = createStaffAndLogin(shopA.token(), "Tecnico A", "tech@partsreq.com", "TECHNICIAN");

        // Create a catalog part in Shop A
        MvcResult catalogRes = mockMvc.perform(post("/api/v1/parts-catalog")
                        .header("Authorization", "Bearer " + shopA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Magnetron 2M219J",
                                "applianceCategory", "Micro-ondas",
                                "averageCost", new BigDecimal("65.00"),
                                "suggestedPrice", new BigDecimal("150.00")
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID catalogPartId = UUID.fromString(objectMapper.readTree(catalogRes.getResponse().getContentAsString()).get("id").asString());

        // Create customer + order item and advance to IN_REPAIR
        UUID customerId = createCustomer(shopA.token(), "Carlos Eduardo", "11977776666");
        JsonNode order = createOrder(shopA.token(), customerId);
        UUID orderId = UUID.fromString(order.get("id").asString());
        UUID itemId = UUID.fromString(order.get("items").get(0).get("id").asString());

        transition(shopA.token(), orderId, itemId, "DIAGNOSING");
        transition(shopA.token(), orderId, itemId, "QUOTE_SENT");
        transition(shopA.token(), orderId, itemId, "APPROVED");
        transition(shopA.token(), orderId, itemId, "IN_REPAIR");

        // 1. Attendant cannot create part requests
        mockMvc.perform(post("/api/v1/orders/{orderId}/items/{itemId}/part-requests", orderId, itemId)
                        .header("Authorization", "Bearer " + attendantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "partName", "Fusivel HV",
                                "quantity", 1
                        ))))
                .andExpect(status().isForbidden());

        // 2. Reject invalid purchase URL (e.g. javascript:)
        mockMvc.perform(post("/api/v1/orders/{orderId}/items/{itemId}/part-requests", orderId, itemId)
                        .header("Authorization", "Bearer " + techToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "partName", "Fusivel HV",
                                "purchaseUrl", "javascript:alert(1)"
                        ))))
                .andExpect(status().isUnprocessableContent());

        // 3. Technician creates part request #1 from catalog and moves item from IN_REPAIR -> WAITING_PARTS
        MvcResult pr1Res = mockMvc.perform(post("/api/v1/orders/{orderId}/items/{itemId}/part-requests", orderId, itemId)
                        .header("Authorization", "Bearer " + techToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "partCatalogId", catalogPartId,
                                "quantity", 1,
                                "supplierName", "Mercado Livre - Peças Express",
                                "purchaseUrl", "https://example.com/magnetron",
                                "moveItemToWaitingParts", true
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.partName").value("Magnetron 2M219J"))
                .andExpect(jsonPath("$.estimatedUnitCost").value(65.00))
                .andExpect(jsonPath("$.estimatedTotalCost").value(65.00))
                .andExpect(jsonPath("$.status").value("TO_ORDER"))
                .andExpect(jsonPath("$.itemStatus").value("WAITING_PARTS"))
                .andExpect(jsonPath("$.customerName").value("Carlos Eduardo"))
                .andReturn();
        UUID pr1Id = UUID.fromString(objectMapper.readTree(pr1Res.getResponse().getContentAsString()).get("id").asString());

        // 4. Technician adds custom part request #2 (qty 2 x 12.50 = 25.00)
        MvcResult pr2Res = mockMvc.perform(post("/api/v1/orders/{orderId}/items/{itemId}/part-requests", orderId, itemId)
                        .header("Authorization", "Bearer " + techToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "partName", "Diodo Alta Tensao",
                                "quantity", 2,
                                "estimatedUnitCost", new BigDecimal("12.50")
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estimatedTotalCost").value(25.00))
                .andReturn();
        UUID pr2Id = UUID.fromString(objectMapper.readTree(pr2Res.getResponse().getContentAsString()).get("id").asString());

        // 5. Attendant can read shopping queue & summary; Tenant B sees nothing
        mockMvc.perform(get("/api/v1/part-requests")
                        .param("status", "TO_ORDER")
                        .header("Authorization", "Bearer " + attendantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));

        mockMvc.perform(get("/api/v1/part-requests/summary")
                        .header("Authorization", "Bearer " + attendantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toOrderCount").value(2))
                .andExpect(jsonPath("$.orderedCount").value(0))
                .andExpect(jsonPath("$.estimatedToOrderCost").value(90.00));

        mockMvc.perform(get("/api/v1/part-requests")
                        .header("Authorization", "Bearer " + shopB.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));

        mockMvc.perform(post("/api/v1/part-requests/{id}/order", pr1Id)
                        .header("Authorization", "Bearer " + shopB.token()))
                .andExpect(status().isNotFound());

        // 6. Mark part #1 as ORDERED -> reopen -> mark ORDERED again with updated supplier/cost
        mockMvc.perform(post("/api/v1/part-requests/{id}/order", pr1Id)
                        .header("Authorization", "Bearer " + shopA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "supplierName", "Distribuidora Central",
                                "actualUnitCost", new BigDecimal("70.00")
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ORDERED"))
                .andExpect(jsonPath("$.actualUnitCost").value(70.00))
                .andExpect(jsonPath("$.orderedAt").isNotEmpty());

        mockMvc.perform(post("/api/v1/part-requests/{id}/reopen", pr1Id)
                        .header("Authorization", "Bearer " + shopA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TO_ORDER"));

        mockMvc.perform(post("/api/v1/part-requests/{id}/order", pr1Id)
                        .header("Authorization", "Bearer " + shopA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "actualUnitCost", new BigDecimal("72.00")
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ORDERED"));

        // 7. Technician is forbidden from recording expense in finance when arriving part
        mockMvc.perform(post("/api/v1/part-requests/{id}/arrive", pr1Id)
                        .header("Authorization", "Bearer " + techToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "recordExpense", true,
                                "paymentMethod", "PIX"
                        ))))
                .andExpect(status().isForbidden());

        // 8. Owner marks part #1 ARRIVED with expense recording -> item stays WAITING_PARTS (part #2 still pending)
        mockMvc.perform(post("/api/v1/part-requests/{id}/arrive", pr1Id)
                        .header("Authorization", "Bearer " + shopA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "actualUnitCost", new BigDecimal("72.00"),
                                "addToItemPartsCost", true,
                                "moveItemToInRepair", true,
                                "recordExpense", true,
                                "paymentMethod", "PIX"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARRIVED"))
                .andExpect(jsonPath("$.expenseRecorded").value(true))
                .andExpect(jsonPath("$.itemStatus").value("WAITING_PARTS"));

        // Verify async Finance expense transaction was created idempotently
        awaitTransactionsCount(shopA.tenantId(), 1);
        mockMvc.perform(get("/api/v1/finance/transactions")
                        .param("category", "PARTS_PURCHASE")
                        .header("Authorization", "Bearer " + shopA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].type").value("EXPENSE"))
                .andExpect(jsonPath("$.content[0].amount").value(72.00))
                .andExpect(jsonPath("$.content[0].paymentMethod").value("PIX"))
                .andExpect(jsonPath("$.content[0].serviceOrderItemId").value(itemId.toString()));

        // Arrived part cannot be edited or deleted
        mockMvc.perform(put("/api/v1/part-requests/{id}", pr1Id)
                        .header("Authorization", "Bearer " + shopA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "partName", "Outro Nome",
                                "quantity", 1,
                                "estimatedUnitCost", new BigDecimal("10.00")
                        ))))
                .andExpect(status().isUnprocessableContent());

        mockMvc.perform(delete("/api/v1/part-requests/{id}", pr1Id)
                        .header("Authorization", "Bearer " + shopA.token()))
                .andExpect(status().isUnprocessableContent());

        // 9. Technician marks part #2 ARRIVED directly from TO_ORDER -> item auto-transitions to IN_REPAIR & partsCostActual = 72 + 25 = 97
        mockMvc.perform(post("/api/v1/part-requests/{id}/arrive", pr2Id)
                        .header("Authorization", "Bearer " + techToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "addToItemPartsCost", true,
                                "moveItemToInRepair", true,
                                "recordExpense", false
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARRIVED"))
                .andExpect(jsonPath("$.itemStatus").value("IN_REPAIR"));

        // Verify item updated on order detail endpoint
        mockMvc.perform(get("/api/v1/orders/{orderId}", orderId)
                        .header("Authorization", "Bearer " + shopA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("IN_REPAIR"))
                .andExpect(jsonPath("$.items[0].partsCostActual").value(97.00));

        // Summary reflects 0 pending, 2 arrived
        mockMvc.perform(get("/api/v1/part-requests/summary")
                        .header("Authorization", "Bearer " + shopA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toOrderCount").value(0))
                .andExpect(jsonPath("$.orderedCount").value(0))
                .andExpect(jsonPath("$.arrivedCount").value(2));
    }

    private void awaitTransactionsCount(UUID tenantId, int expected) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (Instant.now().isBefore(deadline)) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM financial_transactions WHERE tenant_id = ?",
                    Integer.class,
                    tenantId
            );
            if (count != null && count >= expected) {
                return;
            }
            Thread.sleep(50);
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM financial_transactions WHERE tenant_id = ?",
                Integer.class,
                tenantId
        );
        assertThat(count).isEqualTo(expected);
    }

    private record RegisteredShop(UUID tenantId, String token) {
    }

    private RegisteredShop registerShop(String shopName, String doc, String phone, String ownerName, String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "shopName", shopName,
                                "document", doc,
                                "phone", phone,
                                "ownerName", ownerName,
                                "email", email,
                                "password", "Secret123!"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        UUID tenantId = UUID.fromString(json.get("user").get("tenantId").asString());
        return new RegisteredShop(tenantId, json.get("accessToken").asString());
    }

    private String createStaffAndLogin(String ownerToken, String name, String email, String role) throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", name,
                                "email", email,
                                "password", "Secret123!",
                                "role", role
                        ))))
                .andExpect(status().isCreated());

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "Secret123!"
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(login.getResponse().getContentAsString()).get("accessToken").asString();
    }

    private UUID createCustomer(String token, String name, String phone) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", name, "phone", phone))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asString());
    }

    private JsonNode createOrder(String token, UUID customerId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "customerId", customerId,
                                "items", List.of(Map.of(
                                        "applianceType", "Micro-ondas",
                                        "brand", "Brastemp",
                                        "model", "BMS45",
                                        "voltage", "V220",
                                        "reportedIssue", "Não esquenta"
                                ))
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private void transition(String token, UUID orderId, UUID itemId, String status) throws Exception {
        mockMvc.perform(post("/api/v1/orders/{orderId}/items/{itemId}/status", orderId, itemId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", status))))
                .andExpect(status().isOk());
    }
}
