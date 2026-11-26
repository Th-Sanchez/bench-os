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
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class WorkshopAnalyticsIntegrationTest {

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
    void computesActionableWorkshopAnalyticsWithOwnerOnlyAccessAndTenantIsolation() throws Exception {
        String shopAToken = registerShop("Oficina Analytics A", "11222333000181", "11999990001", "Dono A", "a@analytics.com");
        String shopBToken = registerShop("Oficina Analytics B", "44555666000192", "11999990002", "Dono B", "b@analytics.com");
        String techToken = createStaffAndLogin(shopAToken, "Tecnico A", "tech@analytics.com", "TECHNICIAN");

        // 1. RBAC: Technician cannot access analytics
        mockMvc.perform(get("/api/v1/orders/analytics")
                        .header("Authorization", "Bearer " + techToken))
                .andExpect(status().isForbidden());

        UUID customerId = createCustomer(shopAToken, "Fernanda Costa", "11988885555");

        // Item 1: Approved & Delivered paid repair (Revenue 300, Actual Parts 90 -> Profit 210)
        JsonNode order1 = createOrder(shopAToken, customerId, "Micro-ondas");
        UUID order1Id = UUID.fromString(order1.get("id").asString());
        UUID item1Id = UUID.fromString(order1.get("items").get(0).get("id").asString());
        transition(shopAToken, order1Id, item1Id, "DIAGNOSING");
        updateQuote(shopAToken, order1Id, item1Id, "150.00", "150.00", "90.00");
        transition(shopAToken, order1Id, item1Id, "QUOTE_SENT");
        transition(shopAToken, order1Id, item1Id, "APPROVED");
        transition(shopAToken, order1Id, item1Id, "IN_REPAIR");
        transition(shopAToken, order1Id, item1Id, "READY_FOR_PICKUP");
        deliver(shopAToken, order1Id, item1Id, "300.00");

        // Adjust timestamps for deterministic SLA calculation on Item 1:
        // createdAt = now - 30h, quoteSentAt = now - 24h (6h intake->quote),
        // approvedAt = now - 20h, finishedAt = now - 2h (18h approval->ready, 28h total)
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        jdbcTemplate.update(
                "UPDATE service_order_items SET created_at = ?, quote_sent_at = ?, approved_at = ?, finished_at = ? WHERE id = ?",
                Timestamp.from(now.minus(30, ChronoUnit.HOURS)),
                Timestamp.from(now.minus(24, ChronoUnit.HOURS)),
                Timestamp.from(now.minus(20, ChronoUnit.HOURS)),
                Timestamp.from(now.minus(2, ChronoUnit.HOURS)),
                item1Id
        );

        // Item 2: Approved & Delivered paid repair (Revenue 200, Actual Parts 50 -> Profit 150)
        // Average ticket across 2 paid repairs = (300 + 200) / 2 = 250.00
        JsonNode order2 = createOrder(shopAToken, customerId, "Air Fryer");
        UUID order2Id = UUID.fromString(order2.get("id").asString());
        UUID item2Id = UUID.fromString(order2.get("items").get(0).get("id").asString());
        transition(shopAToken, order2Id, item2Id, "DIAGNOSING");
        updateQuote(shopAToken, order2Id, item2Id, "100.00", "100.00", "50.00");
        transition(shopAToken, order2Id, item2Id, "QUOTE_SENT");
        transition(shopAToken, order2Id, item2Id, "APPROVED");
        transition(shopAToken, order2Id, item2Id, "IN_REPAIR");
        transition(shopAToken, order2Id, item2Id, "READY_FOR_PICKUP");
        deliver(shopAToken, order2Id, item2Id, "200.00");

        jdbcTemplate.update(
                "UPDATE service_order_items SET created_at = ?, quote_sent_at = ?, approved_at = ?, finished_at = ? WHERE id = ?",
                Timestamp.from(now.minus(14, ChronoUnit.HOURS)),
                Timestamp.from(now.minus(12, ChronoUnit.HOURS)),
                Timestamp.from(now.minus(10, ChronoUnit.HOURS)),
                Timestamp.from(now.minus(4, ChronoUnit.HOURS)),
                item2Id
        );

        // Item 3: Rejected quote and delivered without charge (still counts as rejected in conversion rate!)
        JsonNode order3 = createOrder(shopAToken, customerId, "Cafeteira");
        UUID order3Id = UUID.fromString(order3.get("id").asString());
        UUID item3Id = UUID.fromString(order3.get("items").get(0).get("id").asString());
        transition(shopAToken, order3Id, item3Id, "DIAGNOSING");
        updateQuote(shopAToken, order3Id, item3Id, "80.00", "70.00", "0.00");
        transition(shopAToken, order3Id, item3Id, "QUOTE_SENT");
        transition(shopAToken, order3Id, item3Id, "REJECTED");
        mockMvc.perform(post("/api/v1/orders/{orderId}/items/{itemId}/deliver", order3Id, item3Id)
                        .header("Authorization", "Bearer " + shopAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        jdbcTemplate.update(
                "UPDATE service_order_items SET created_at = ?, quote_sent_at = ? WHERE id = ?",
                Timestamp.from(now.minus(10, ChronoUnit.HOURS)),
                Timestamp.from(now.minus(6, ChronoUnit.HOURS)),
                item3Id
        );

        // Item 4: Warranty return from Item 1 (currently in WAITING_PARTS and overdue!)
        MvcResult wrResult = mockMvc.perform(post("/api/v1/orders/{orderId}/items/{itemId}/warranty-return", order1Id, item1Id)
                        .header("Authorization", "Bearer " + shopAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "reportedIssue", "Voltou a parar de aquecer"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode wrOrder = objectMapper.readTree(wrResult.getResponse().getContentAsString());
        UUID wrOrderId = UUID.fromString(wrOrder.get("id").asString());
        UUID wrItemId = UUID.fromString(wrOrder.get("items").get(0).get("id").asString());
        transition(shopAToken, wrOrderId, wrItemId, "DIAGNOSING");
        transition(shopAToken, wrOrderId, wrItemId, "APPROVED");
        transition(shopAToken, wrOrderId, wrItemId, "WAITING_PARTS");

        LocalDate todaySp = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        jdbcTemplate.update(
                "UPDATE service_order_items SET estimated_completion_date = ? WHERE id = ?",
                todaySp.minusDays(2),
                wrItemId
        );

        // Query analytics for current month
        // - Quotes: 3 sent (2 approved, 1 rejected) -> approvalRate = 2/3 = 66.67%
        // - SLA intake->quote: (6 + 2 + 4) / 3 = 4.0h
        // - SLA approval->ready: (18 + 6) / 2 = 12.0h
        // - Delivered: 3 total (2 paid, 1 rejected free), Revenue = 500, PartsActual = 140, NetProfit = 360, Margin = 72.00%, AvgTicket = 250.00
        // - Received: 4 items, 1 warranty return -> 25.00%
        // - Live bottlenecks: 1 waitingParts, 1 overdueActive
        mockMvc.perform(get("/api/v1/orders/analytics")
                        .param("from", todaySp.withDayOfMonth(1).toString())
                        .param("to", todaySp.withDayOfMonth(todaySp.lengthOfMonth()).toString())
                        .param("zone", "America/Sao_Paulo")
                        .header("Authorization", "Bearer " + shopAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quotesSentCount").value(3))
                .andExpect(jsonPath("$.quotesApprovedCount").value(2))
                .andExpect(jsonPath("$.quotesRejectedCount").value(1))
                .andExpect(jsonPath("$.quotesPendingCount").value(0))
                .andExpect(jsonPath("$.quoteApprovalRatePercent").value(66.67))
                .andExpect(jsonPath("$.avgIntakeToQuoteHours").value(4.0))
                .andExpect(jsonPath("$.intakeToQuoteSampleSize").value(3))
                .andExpect(jsonPath("$.avgApprovalToReadyHours").value(12.0))
                .andExpect(jsonPath("$.approvalToReadySampleSize").value(2))
                .andExpect(jsonPath("$.deliveredItemsCount").value(3))
                .andExpect(jsonPath("$.paidRepairsCount").value(2))
                .andExpect(jsonPath("$.totalRevenue").value(500.00))
                .andExpect(jsonPath("$.totalPartsCostActual").value(140.00))
                .andExpect(jsonPath("$.netRepairProfit").value(360.00))
                .andExpect(jsonPath("$.netProfitMarginPercent").value(72.00))
                .andExpect(jsonPath("$.averageTicket").value(250.00))
                .andExpect(jsonPath("$.itemsReceivedCount").value(4))
                .andExpect(jsonPath("$.warrantyReturnsCount").value(1))
                .andExpect(jsonPath("$.warrantyReturnRatePercent").value(25.00))
                .andExpect(jsonPath("$.waitingPartsCount").value(1))
                .andExpect(jsonPath("$.overdueActiveCount").value(1));

        // Tenant B sees clean zeros / nulls
        mockMvc.perform(get("/api/v1/orders/analytics")
                        .header("Authorization", "Bearer " + shopBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemsReceivedCount").value(0))
                .andExpect(jsonPath("$.deliveredItemsCount").value(0))
                .andExpect(jsonPath("$.quoteApprovalRatePercent").doesNotExist());
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

    private JsonNode createOrder(String token, UUID customerId, String applianceType) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "customerId", customerId,
                                "items", List.of(Map.of(
                                        "applianceType", applianceType,
                                        "brand", "Mondial",
                                        "voltage", "V110",
                                        "reportedIssue", "Não liga"
                                ))
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private void updateQuote(String token, UUID orderId, UUID itemId, String labor, String parts, String actual) throws Exception {
        mockMvc.perform(patch("/api/v1/orders/{orderId}/items/{itemId}/quote", orderId, itemId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "technicalDiagnosis", "Reparo placa",
                                "laborCost", new BigDecimal(labor),
                                "partsCost", new BigDecimal(parts),
                                "partsCostActual", new BigDecimal(actual),
                                "discount", BigDecimal.ZERO,
                                "warrantyDays", 90
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

    private void deliver(String token, UUID orderId, UUID itemId, String amount) throws Exception {
        mockMvc.perform(post("/api/v1/orders/{orderId}/items/{itemId}/deliver", orderId, itemId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "payments", List.of(Map.of("paymentMethod", "PIX", "amount", new BigDecimal(amount)))
                        ))))
                .andExpect(status().isOk());
    }
}
