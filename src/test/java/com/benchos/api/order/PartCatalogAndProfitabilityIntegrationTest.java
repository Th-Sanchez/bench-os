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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PartCatalogAndProfitabilityIntegrationTest {

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
        jdbcTemplate.execute("DELETE FROM financial_transactions");
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
    void managesPartsCatalogWithTenantIsolationAndComputesRealRepairProfitability() throws Exception {
        String shopAToken = registerShop("Oficina Alfa", "11222333000181", "11999990001", "Dono Alfa", "alfa@parts.com");
        String shopBToken = registerShop("Oficina Beta", "44555666000192", "11999990002", "Dono Beta", "beta@parts.com");
        String attendantToken = createStaffAndLogin(shopAToken, "Atendente Alfa", "atendente@parts.com", "ATTENDANT");

        // 1. Seed defaults is idempotent
        MvcResult seed1 = mockMvc.perform(post("/api/v1/parts-catalog/seed-defaults")
                        .header("Authorization", "Bearer " + shopAToken))
                .andExpect(status().isOk())
                .andReturn();
        int seededCount = objectMapper.readTree(seed1.getResponse().getContentAsString()).size();
        assertThat(seededCount).isGreaterThanOrEqualTo(8);

        MvcResult seed2 = mockMvc.perform(post("/api/v1/parts-catalog/seed-defaults")
                        .header("Authorization", "Bearer " + shopAToken))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(objectMapper.readTree(seed2.getResponse().getContentAsString()).size()).isEqualTo(seededCount);

        // 2. Attendant can read catalog (including multi-appliance category filtering), cannot write or view profitability
        mockMvc.perform(get("/api/v1/parts-catalog")
                        .param("category", "Air Fryer")
                        .header("Authorization", "Bearer " + attendantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));

        mockMvc.perform(post("/api/v1/parts-catalog")
                        .header("Authorization", "Bearer " + attendantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Termostato",
                                "averageCost", new BigDecimal("15.00"),
                                "suggestedPrice", new BigDecimal("45.00")
                        ))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/orders/profitability")
                        .header("Authorization", "Bearer " + attendantToken))
                .andExpect(status().isForbidden());

        // 3. Create, duplicate rejection, update, cross-tenant isolation, and soft-delete
        MvcResult createdPartResult = mockMvc.perform(post("/api/v1/parts-catalog")
                        .header("Authorization", "Bearer " + shopAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Placa Inversora Especial",
                                "applianceCategory", "Micro-ondas",
                                "averageCost", new BigDecimal("80.00"),
                                "suggestedPrice", new BigDecimal("190.00"),
                                "notes", "Original Panasonic"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.unitProfit").value(110.00))
                .andReturn();
        UUID partId = UUID.fromString(objectMapper.readTree(createdPartResult.getResponse().getContentAsString()).get("id").asString());

        mockMvc.perform(post("/api/v1/parts-catalog")
                        .header("Authorization", "Bearer " + shopAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "placa inversora especial",
                                "averageCost", new BigDecimal("70.00"),
                                "suggestedPrice", new BigDecimal("180.00")
                        ))))
                .andExpect(status().isUnprocessableContent());

        mockMvc.perform(put("/api/v1/parts-catalog/{id}", partId)
                        .header("Authorization", "Bearer " + shopBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Hack",
                                "averageCost", new BigDecimal("1.00"),
                                "suggestedPrice", new BigDecimal("2.00")
                        ))))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/v1/parts-catalog/{id}", partId)
                        .header("Authorization", "Bearer " + shopAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Placa Inversora Panasonic",
                                "applianceCategory", "Micro-ondas",
                                "averageCost", new BigDecimal("85.00"),
                                "suggestedPrice", new BigDecimal("200.00")
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unitProfit").value(115.00));

        mockMvc.perform(delete("/api/v1/parts-catalog/{id}", partId)
                        .header("Authorization", "Bearer " + shopAToken))
                .andExpect(status().isNoContent());

        // 4. Quote with actual parts cost -> verify estimatedNetProfit and public endpoint hides actual cost
        UUID customerId = createCustomer(shopAToken, "Roberto Mendes", "11988887777");
        JsonNode order = createOrder(shopAToken, customerId);
        UUID orderId = UUID.fromString(order.get("id").asString());
        UUID itemId = UUID.fromString(order.get("items").get(0).get("id").asString());
        String publicToken = order.get("publicToken").asString();

        transition(shopAToken, orderId, itemId, "DIAGNOSING");

        mockMvc.perform(patch("/api/v1/orders/{orderId}/items/{itemId}/quote", orderId, itemId)
                        .header("Authorization", "Bearer " + shopAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "technicalDiagnosis", "Troca de magnetron e fusível HV",
                                "laborCost", new BigDecimal("120.00"),
                                "partsCost", new BigDecimal("155.00"),
                                "partsCostActual", new BigDecimal("71.00"),
                                "discount", new BigDecimal("15.00"),
                                "warrantyDays", 90
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPrice").value(260.00))
                .andExpect(jsonPath("$.partsCostActual").value(71.00))
                .andExpect(jsonPath("$.estimatedNetProfit").value(189.00));

        // Public tracking response must NEVER expose internal partsCostActual or estimatedNetProfit
        mockMvc.perform(get("/api/v1/public/orders/{token}", publicToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].partsCostActual").doesNotExist())
                .andExpect(jsonPath("$.items[0].estimatedNetProfit").doesNotExist());

        // Deliver the item so it appears in real profitability report
        transition(shopAToken, orderId, itemId, "QUOTE_SENT");
        transition(shopAToken, orderId, itemId, "APPROVED");
        transition(shopAToken, orderId, itemId, "IN_REPAIR");
        transition(shopAToken, orderId, itemId, "READY_FOR_PICKUP");

        mockMvc.perform(post("/api/v1/orders/{orderId}/items/{itemId}/deliver", orderId, itemId)
                        .header("Authorization", "Bearer " + shopAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "payments", List.of(Map.of("paymentMethod", "PIX", "amount", new BigDecimal("260.00")))
                        ))))
                .andExpect(status().isOk());

        LocalDate todaySp = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        mockMvc.perform(get("/api/v1/orders/profitability")
                        .param("from", todaySp.withDayOfMonth(1).toString())
                        .param("to", todaySp.withDayOfMonth(todaySp.lengthOfMonth()).toString())
                        .param("zone", "America/Sao_Paulo")
                        .header("Authorization", "Bearer " + shopAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveredItemsCount").value(1))
                .andExpect(jsonPath("$.itemsWithPartsCostCount").value(1))
                .andExpect(jsonPath("$.totalRevenue").value(260.00))
                .andExpect(jsonPath("$.totalPartsCostActual").value(71.00))
                .andExpect(jsonPath("$.estimatedNetProfit").value(189.00))
                .andExpect(jsonPath("$.profitMarginPercent").value(72.69));

        // Tenant B sees zero delivered items
        mockMvc.perform(get("/api/v1/orders/profitability")
                        .param("from", todaySp.withDayOfMonth(1).toString())
                        .param("to", todaySp.withDayOfMonth(todaySp.lengthOfMonth()).toString())
                        .header("Authorization", "Bearer " + shopBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveredItemsCount").value(0))
                .andExpect(jsonPath("$.totalRevenue").value(0));
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
