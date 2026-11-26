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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TechnicianCommissionIntegrationTest {

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
    void managesOptionalTechnicianAssignmentImmutableCommissionSnapshotAndMonthlyClosing() throws Exception {
        String ownerToken = registerShop("Oficina Comissao A", "11222333000181", "11999990001", "Dono A", "a@comm.com");
        String shopBToken = registerShop("Oficina Comissao B", "44555666000192", "11999990002", "Dono B", "b@comm.com");

        // 1. Default settings: OFF by default for solo shops
        mockMvc.perform(get("/api/v1/tenant/settings")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.technicianAssignmentEnabled").value(false))
                .andExpect(jsonPath("$.commissionBasis").value("LABOR_ONLY"));

        // Enable technician assignment (keep LABOR_ONLY basis)
        mockMvc.perform(put("/api/v1/tenant/settings")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "defaultLaborCost", new BigDecimal("80.00"),
                                "defaultEstimatedDays", 3,
                                "abandonmentDays", 180,
                                "whatsappQuoteTemplate", "Olá {customerName}",
                                "technicianAssignmentEnabled", true,
                                "commissionBasis", "LABOR_ONLY"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.technicianAssignmentEnabled").value(true))
                .andExpect(jsonPath("$.commissionBasis").value("LABOR_ONLY"));

        // 2. Create Technician (30% commission) and Attendant
        StaffMember tech = createStaff(ownerToken, "Lucas Bancada", "lucas@comm.com", "TECHNICIAN", new BigDecimal("30.00"));
        StaffMember attendant = createStaff(ownerToken, "Ana Balcao", "ana@comm.com", "ATTENDANT", BigDecimal.ZERO);
        StaffMember techShopB = createStaff(shopBToken, "Outro Tec", "outro@comm.com", "TECHNICIAN", new BigDecimal("25.00"));

        // Attendant can list assignable technicians (only OWNER + TECHNICIAN, no commission exposed)
        mockMvc.perform(get("/api/v1/users/technicians")
                        .header("Authorization", "Bearer " + attendant.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].commissionPercent").doesNotExist());

        // Non-owner cannot view monthly commissions or update user commission %
        mockMvc.perform(get("/api/v1/orders/commissions")
                        .header("Authorization", "Bearer " + tech.token()))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/users/{id}/commission", tech.id())
                        .header("Authorization", "Bearer " + tech.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("commissionPercent", new BigDecimal("50.00")))))
                .andExpect(status().isForbidden());

        // 3. Create 2 orders with 2 items + 1 unassigned delivered item
        UUID customerId = createCustomer(ownerToken, "Paulo Henrique", "11988884444");
        JsonNode order1 = createOrder(ownerToken, customerId);
        UUID order1Id = UUID.fromString(order1.get("id").asString());
        UUID item1Id = UUID.fromString(order1.get("items").get(0).get("id").asString());

        // Cannot assign an ATTENDANT as repair technician
        mockMvc.perform(patch("/api/v1/orders/{orderId}/items/{itemId}/technician", order1Id, item1Id)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("technicianId", attendant.id()))))
                .andExpect(status().isUnprocessableContent());

        // Cannot assign technician from another tenant
        mockMvc.perform(patch("/api/v1/orders/{orderId}/items/{itemId}/technician", order1Id, item1Id)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("technicianId", techShopB.id()))))
                .andExpect(status().isNotFound());

        // Attendant cannot pass custom commission override
        mockMvc.perform(patch("/api/v1/orders/{orderId}/items/{itemId}/technician", order1Id, item1Id)
                        .header("Authorization", "Bearer " + attendant.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "technicianId", tech.id(),
                                "commissionPercent", new BigDecimal("40.00")
                        ))))
                .andExpect(status().isForbidden());

        // Attendant assigns Lucas without override -> succeeds (commissionPercent null for attendant!)
        mockMvc.perform(patch("/api/v1/orders/{orderId}/items/{itemId}/technician", order1Id, item1Id)
                        .header("Authorization", "Bearer " + attendant.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("technicianId", tech.id()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedTechnicianId").value(tech.id().toString()))
                .andExpect(jsonPath("$.assignedTechnicianName").value("Lucas Bancada"))
                .andExpect(jsonPath("$.commissionPercent").doesNotExist());

        // Item 1: Labor 200, Parts 100, Actual 50, Total 300 -> 30% over LABOR_ONLY = 60.00
        completeAndDeliver(ownerToken, order1Id, item1Id, "200.00", "100.00", "50.00", "300.00");

        // Verify commission was snapshotted on Item 1 (OWNER sees it, TECHNICIAN doesn't on order detail)
        mockMvc.perform(get("/api/v1/orders/{orderId}", order1Id)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].commissionPercent").value(30.00))
                .andExpect(jsonPath("$.items[0].commissionAmount").value(60.00));

        // Change Lucas's default commission to 40% AFTER Item 1 delivery -> Item 1 stays 60.00 (immutable!)
        mockMvc.perform(patch("/api/v1/users/{id}/commission", tech.id())
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("commissionPercent", new BigDecimal("40.00")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commissionPercent").value(40.00));

        // Item 2: Assigned to Lucas with per-item override 25% over Labor 160 = 40.00
        JsonNode order2 = createOrder(ownerToken, customerId);
        UUID order2Id = UUID.fromString(order2.get("id").asString());
        UUID item2Id = UUID.fromString(order2.get("items").get(0).get("id").asString());
        mockMvc.perform(patch("/api/v1/orders/{orderId}/items/{itemId}/technician", order2Id, item2Id)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "technicianId", tech.id(),
                                "commissionPercent", new BigDecimal("25.00")
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commissionPercent").value(25.00));
        completeAndDeliver(ownerToken, order2Id, item2Id, "160.00", "40.00", "20.00", "200.00");

        // Item 3: Unassigned paid delivery
        JsonNode order3 = createOrder(ownerToken, customerId);
        UUID order3Id = UUID.fromString(order3.get("id").asString());
        UUID item3Id = UUID.fromString(order3.get("items").get(0).get("id").asString());
        completeAndDeliver(ownerToken, order3Id, item3Id, "100.00", "0.00", "0.00", "100.00");

        // 4. Verify monthly commission closing report
        // Lucas total commission = 60.00 (item 1) + 40.00 (item 2) = 100.00
        LocalDate todaySp = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        mockMvc.perform(get("/api/v1/orders/commissions")
                        .param("from", todaySp.withDayOfMonth(1).toString())
                        .param("to", todaySp.withDayOfMonth(todaySp.lengthOfMonth()).toString())
                        .param("zone", "America/Sao_Paulo")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.technicianAssignmentEnabled").value(true))
                .andExpect(jsonPath("$.commissionBasis").value("LABOR_ONLY"))
                .andExpect(jsonPath("$.deliveredWithTechnicianCount").value(2))
                .andExpect(jsonPath("$.deliveredUnassignedCount").value(1))
                .andExpect(jsonPath("$.totalCommissionableRevenue").value(500.00))
                .andExpect(jsonPath("$.totalCommissionAmount").value(100.00))
                .andExpect(jsonPath("$.technicians.length()").value(1))
                .andExpect(jsonPath("$.technicians[0].technicianName").value("Lucas Bancada"))
                .andExpect(jsonPath("$.technicians[0].deliveredItemsCount").value(2))
                .andExpect(jsonPath("$.technicians[0].commissionTotal").value(100.00))
                .andExpect(jsonPath("$.technicians[0].items.length()").value(2));
    }

    private record StaffMember(UUID id, String token) {
    }

    private String registerShop(String shopName, String doc, String phone, String ownerName, String email) throws Exception {
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
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asString();
    }

    private StaffMember createStaff(String ownerToken, String name, String email, String role, BigDecimal commission) throws Exception {
        MvcResult createRes = mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", name,
                                "email", email,
                                "password", "Secret123!",
                                "role", role,
                                "commissionPercent", commission
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID id = UUID.fromString(objectMapper.readTree(createRes.getResponse().getContentAsString()).get("id").asString());

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "Secret123!"
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        return new StaffMember(id, objectMapper.readTree(login.getResponse().getContentAsString()).get("accessToken").asString());
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
                                        "brand", "Electrolux",
                                        "voltage", "V110",
                                        "reportedIssue", "Não aquece"
                                ))
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private void completeAndDeliver(
            String token,
            UUID orderId,
            UUID itemId,
            String labor,
            String parts,
            String actual,
            String total
    ) throws Exception {
        transition(token, orderId, itemId, "DIAGNOSING");
        mockMvc.perform(patch("/api/v1/orders/{orderId}/items/{itemId}/quote", orderId, itemId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "technicalDiagnosis", "Reparo concluído",
                                "laborCost", new BigDecimal(labor),
                                "partsCost", new BigDecimal(parts),
                                "partsCostActual", new BigDecimal(actual),
                                "discount", BigDecimal.ZERO,
                                "warrantyDays", 90
                        ))))
                .andExpect(status().isOk());
        transition(token, orderId, itemId, "QUOTE_SENT");
        transition(token, orderId, itemId, "APPROVED");
        transition(token, orderId, itemId, "IN_REPAIR");
        transition(token, orderId, itemId, "READY_FOR_PICKUP");
        mockMvc.perform(post("/api/v1/orders/{orderId}/items/{itemId}/deliver", orderId, itemId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "payments", List.of(Map.of("paymentMethod", "PIX", "amount", new BigDecimal(total)))
                        ))))
                .andExpect(status().isOk());
    }

    private void transition(String token, UUID orderId, UUID itemId, String status) throws Exception {
        mockMvc.perform(post("/api/v1/orders/{orderId}/items/{itemId}/status", orderId, itemId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", status))))
                .andExpect(status().isOk());
    }
}
