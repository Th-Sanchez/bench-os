package com.benchos.api.finance;

import com.benchos.api.TestcontainersConfiguration;
import com.benchos.api.finance.internal.entity.FinancialTransaction;
import com.benchos.api.finance.internal.repository.FinancialTransactionRepository;
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
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
class FinanceIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FinancialTransactionRepository transactionRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

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
        awaitPendingEventPublications();
        TenantContext.clear();
        jdbcTemplate.execute("DELETE FROM financial_transactions");
        jdbcTemplate.execute("DELETE FROM suppliers");
        jdbcTemplate.execute("DELETE FROM event_publication");
        jdbcTemplate.execute("DELETE FROM service_order_items");
        jdbcTemplate.execute("DELETE FROM service_orders");
        jdbcTemplate.execute("DELETE FROM tenant_sequences");
        jdbcTemplate.execute("DELETE FROM customers");
        jdbcTemplate.execute("DELETE FROM users");
        jdbcTemplate.execute("DELETE FROM tenant_settings");
        jdbcTemplate.execute("DELETE FROM tenants");
    }

    @Test
    void recordsAutomaticRepairIncomeFromSplitPaymentTracksStoreCreditAndComputesOwnerOnlySummary() throws Exception {
        RegisteredShop shopA = registerShop("Oficina A", "12345678000101", "11999990001", "Carlos", "carlos@shopa.com", "Secret123!");
        RegisteredShop shopB = registerShop("Oficina B", "98765432000102", "11999990002", "Marcos", "marcos@shopb.com", "Secret123!");

        // Create attendant and technician in Shop A to test strict OWNER RBAC
        createUser(shopA.token(), "Ana Balcao", "ana@shopa.com", "AttPass123!", "ATTENDANT");
        createUser(shopA.token(), "Lucas Tecnico", "lucas@shopa.com", "TechPass123!", "TECHNICIAN");
        String attendantToken = login("ana@shopa.com", "AttPass123!");
        String techToken = login("lucas@shopa.com", "TechPass123!");

        // 1. RBAC: ATTENDANT and TECHNICIAN get 403 Forbidden on financial summary, transactions, and suppliers
        for (String nonOwnerToken : List.of(attendantToken, techToken)) {
            mockMvc.perform(get("/api/v1/finance/summary")
                            .header("Authorization", "Bearer " + nonOwnerToken))
                    .andExpect(status().isForbidden());

            mockMvc.perform(get("/api/v1/finance/transactions")
                            .header("Authorization", "Bearer " + nonOwnerToken))
                    .andExpect(status().isForbidden());

            mockMvc.perform(get("/api/v1/finance/suppliers")
                            .header("Authorization", "Bearer " + nonOwnerToken))
                    .andExpect(status().isForbidden());
        }

        // 2. Deliver a repaired OS item in Shop A with split payment (100.00 PIX + 150.00 CREDIT_CARD = 250.00)
        UUID customerA = createCustomer(shopA.token(), "Maria Silva", "(11) 98888-1111");
        JsonNode order = createOrder(shopA.token(), customerA);
        UUID orderId = UUID.fromString(order.get("id").asString());
        UUID itemId = UUID.fromString(order.get("items").get(0).get("id").asString());

        transitionItem(shopA.token(), orderId, itemId, "DIAGNOSING");
        mockMvc.perform(patch("/api/v1/orders/{orderId}/items/{itemId}/quote", orderId, itemId)
                        .header("Authorization", "Bearer " + shopA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "technicalDiagnosis", "Replaced magnetron",
                                "laborCost", new BigDecimal("100.00"),
                                "partsCost", new BigDecimal("150.00"),
                                "discount", new BigDecimal("0.00"),
                                "warrantyDays", 90
                        ))))
                .andExpect(status().isOk());
        transitionItem(shopA.token(), orderId, itemId, "QUOTE_SENT");
        transitionItem(shopA.token(), orderId, itemId, "APPROVED");
        transitionItem(shopA.token(), orderId, itemId, "IN_REPAIR");
        transitionItem(shopA.token(), orderId, itemId, "READY_FOR_PICKUP");

        mockMvc.perform(post("/api/v1/orders/{orderId}/items/{itemId}/deliver", orderId, itemId)
                        .header("Authorization", "Bearer " + shopA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "payments", List.of(
                                        Map.of("paymentMethod", "PIX", "amount", new BigDecimal("100.00")),
                                        Map.of("paymentMethod", "CREDIT_CARD", "amount", new BigDecimal("150.00"))
                                )
                        ))))
                .andExpect(status().isOk());

        // Wait for Spring Modulith async @ApplicationModuleListener to process OrderItemDeliveredEvent
        awaitTransactionsCount(shopA.tenantId(), 2);
        awaitPendingEventPublications();

        Integer completedEvents = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM event_publication WHERE completion_date IS NOT NULL",
                Integer.class
        );
        assertThat(completedEvents).isGreaterThanOrEqualTo(1);

        // 3. Supplier CRUD, uniqueness, soft delete, and cross-tenant isolation
        UUID supplierAId = createSupplier(shopA.token(), "Casa das Pecas", "(11) 3333-4444", "30-day credit");
        UUID supplierBId = createSupplier(shopB.token(), "Casa das Pecas", "(11) 5555-6666", "Shop B supplier");

        // Duplicate supplier name in same shop -> 409
        mockMvc.perform(post("/api/v1/finance/suppliers")
                        .header("Authorization", "Bearer " + shopA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "casa das pecas"
                        ))))
                .andExpect(status().isConflict());

        // Cross-tenant GET supplier -> 404
        mockMvc.perform(get("/api/v1/finance/suppliers/{id}", supplierBId)
                        .header("Authorization", "Bearer " + shopA.token()))
                .andExpect(status().isNotFound());

        // Update supplier
        mockMvc.perform(put("/api/v1/finance/suppliers/{id}", supplierAId)
                        .header("Authorization", "Bearer " + shopA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Casa das Pecas Central",
                                "phone", "(11) 3333-4444",
                                "notes", "Updated terms"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Casa das Pecas Central"))
                .andExpect(jsonPath("$.phone").value("1133334444"));

        // 4. Expenses & "Fiado" (STORE_CREDIT)
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        // STORE_CREDIT without supplier -> 422
        mockMvc.perform(post("/api/v1/finance/transactions")
                        .header("Authorization", "Bearer " + shopA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "type", "EXPENSE",
                                "category", "PARTS_PURCHASE",
                                "description", "Missing supplier",
                                "amount", new BigDecimal("40.00"),
                                "paymentMethod", "STORE_CREDIT",
                                "dueDate", today.plusDays(15).toString()
                        ))))
                .andExpect(status().is(422));

        // STORE_CREDIT referencing Shop B's supplier -> 404
        mockMvc.perform(post("/api/v1/finance/transactions")
                        .header("Authorization", "Bearer " + shopA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "type", "EXPENSE",
                                "category", "PARTS_PURCHASE",
                                "supplierId", supplierBId,
                                "description", "Cross-tenant supplier",
                                "amount", new BigDecimal("40.00"),
                                "paymentMethod", "STORE_CREDIT",
                                "dueDate", today.plusDays(15).toString()
                        ))))
                .andExpect(status().isNotFound());

        // Paid operational expense: R$ 50.00 CASH
        mockMvc.perform(post("/api/v1/finance/transactions")
                        .header("Authorization", "Bearer " + shopA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "type", "EXPENSE",
                                "category", "OPERATIONAL_EXPENSE",
                                "description", "Cleaning supplies",
                                "amount", new BigDecimal("50.00"),
                                "paymentMethod", "CASH"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.paidAt").isNotEmpty());

        // Overdue STORE_CREDIT parts purchase: R$ 80.00 due 5 days ago
        UUID overdueTxId = createStoreCreditExpense(
                shopA.token(), supplierAId, itemId, "Magnetron fiado (vencido)", "80.00", today.minusDays(5)
        );

        // Upcoming STORE_CREDIT parts purchase: R$ 60.00 due in 10 days (will be paid below)
        UUID upcomingTxId = createStoreCreditExpense(
                shopA.token(), supplierAId, null, "Capacitors fiado", "60.00", today.plusDays(10)
        );

        // Query overdue payables -> returns only overdueTxId
        mockMvc.perform(get("/api/v1/finance/transactions")
                        .param("overdueOnly", "true")
                        .header("Authorization", "Bearer " + shopA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(overdueTxId.toString()))
                .andExpect(jsonPath("$.content[0].supplierName").value("Casa das Pecas Central"));

        // Settle upcomingTxId (R$ 60.00) via PIX
        mockMvc.perform(post("/api/v1/finance/transactions/{id}/pay", upcomingTxId)
                        .header("Authorization", "Bearer " + shopA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("paymentMethod", "PIX"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.paymentMethod").value("PIX"))
                .andExpect(jsonPath("$.paidAt").isNotEmpty());

        // Settling again -> 422
        mockMvc.perform(post("/api/v1/finance/transactions/{id}/pay", upcomingTxId)
                        .header("Authorization", "Bearer " + shopA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("paymentMethod", "CASH"))))
                .andExpect(status().is(422));

        // 5. Financial Summary for Shop A:
        //    totalIncomePaid = 250.00 (100.00 PIX + 150.00 CREDIT_CARD)
        //    totalExpensePaid = 110.00 (50.00 OPERATIONAL_EXPENSE + 60.00 PARTS_PURCHASE)
        //    netBalance = 140.00
        //    totalPendingPayables = 80.00 (overdueTxId)
        //    totalOverduePayables = 80.00 (overdueTxId)
        mockMvc.perform(get("/api/v1/finance/summary")
                        .param("from", today.minusDays(1).toString())
                        .param("to", today.plusDays(1).toString())
                        .header("Authorization", "Bearer " + shopA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalIncomePaid").value(250.00))
                .andExpect(jsonPath("$.totalExpensePaid").value(110.00))
                .andExpect(jsonPath("$.netBalance").value(140.00))
                .andExpect(jsonPath("$.totalPendingPayables").value(80.00))
                .andExpect(jsonPath("$.totalOverduePayables").value(80.00))
                .andExpect(jsonPath("$.incomeByPaymentMethod.PIX").value(100.00))
                .andExpect(jsonPath("$.incomeByPaymentMethod.CREDIT_CARD").value(150.00))
                .andExpect(jsonPath("$.expenseByCategory.OPERATIONAL_EXPENSE").value(50.00))
                .andExpect(jsonPath("$.expenseByCategory.PARTS_PURCHASE").value(60.00));

        // 6. Cross-tenant isolation: Shop B summary is all zeros & cannot view Shop A transactions
        mockMvc.perform(get("/api/v1/finance/summary")
                        .header("Authorization", "Bearer " + shopB.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalIncomePaid").value(0.00))
                .andExpect(jsonPath("$.totalExpensePaid").value(0.00))
                .andExpect(jsonPath("$.netBalance").value(0.00))
                .andExpect(jsonPath("$.totalPendingPayables").value(0.00));

        mockMvc.perform(get("/api/v1/finance/transactions/{id}", overdueTxId)
                        .header("Authorization", "Bearer " + shopB.token()))
                .andExpect(status().isNotFound());

        // Soft delete supplier in Shop A hides it from list & allows re-registering same name
        mockMvc.perform(delete("/api/v1/finance/suppliers/{id}", supplierAId)
                        .header("Authorization", "Bearer " + shopA.token()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/finance/suppliers")
                        .header("Authorization", "Bearer " + shopA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        createSupplier(shopA.token(), "Casa das Pecas Central", null, "Re-created");

        // Hibernate @Filter isolates plain findAll() on financial_transactions
        TenantContext.setCurrentTenant(shopB.tenantId());
        try {
            List<FinancialTransaction> shopBTxs = transactionTemplate.execute(s -> transactionRepository.findAll());
            assertThat(shopBTxs).isEmpty();
        } finally {
            TenantContext.clear();
        }
    }

    private void awaitTransactionsCount(UUID tenantId, int expectedMinCount) {
        long deadline = System.currentTimeMillis() + 5_000L;
        while (System.currentTimeMillis() < deadline) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM financial_transactions WHERE tenant_id = ?",
                    Integer.class,
                    tenantId
            );
            if (count != null && count >= expectedMinCount) {
                return;
            }
            try {
                Thread.sleep(25);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        Integer actual = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM financial_transactions WHERE tenant_id = ?",
                Integer.class,
                tenantId
        );
        assertThat(actual).isGreaterThanOrEqualTo(expectedMinCount);
    }

    private void awaitPendingEventPublications() {
        long deadline = System.currentTimeMillis() + 5_000L;
        while (System.currentTimeMillis() < deadline) {
            Integer pending = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM event_publication WHERE completion_date IS NULL",
                    Integer.class
            );
            if (pending == null || pending == 0) {
                return;
            }
            try {
                Thread.sleep(25);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private UUID createSupplier(String token, String name, String phone, String notes) throws Exception {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("name", name);
        if (phone != null) {
            body.put("phone", phone);
        }
        if (notes != null) {
            body.put("notes", notes);
        }
        MvcResult result = mockMvc.perform(post("/api/v1/finance/suppliers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();

        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asString());
    }

    private UUID createStoreCreditExpense(
            String token, UUID supplierId, UUID itemId, String description, String amount, LocalDate dueDate
    ) throws Exception {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("type", "EXPENSE");
        body.put("category", "PARTS_PURCHASE");
        if (itemId != null) {
            body.put("serviceOrderItemId", itemId);
        }
        body.put("supplierId", supplierId);
        body.put("description", description);
        body.put("amount", new BigDecimal(amount));
        body.put("paymentMethod", "STORE_CREDIT");
        body.put("dueDate", dueDate.toString());

        MvcResult result = mockMvc.perform(post("/api/v1/finance/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asString());
    }

    private void transitionItem(String token, UUID orderId, UUID itemId, String status) throws Exception {
        mockMvc.perform(post("/api/v1/orders/{orderId}/items/{itemId}/status", orderId, itemId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", status))))
                .andExpect(status().isOk());
    }

    private RegisteredShop registerShop(
            String shopName, String document, String phone, String ownerName, String email, String password
    ) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "shopName", shopName,
                                "document", document,
                                "phone", phone,
                                "ownerName", ownerName,
                                "email", email,
                                "password", password
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return new RegisteredShop(
                UUID.fromString(json.get("tenant").get("id").asString()),
                json.get("accessToken").asString()
        );
    }

    private void createUser(String ownerToken, String name, String email, String password, String role) throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", name,
                                "email", email,
                                "password", password,
                                "role", role
                        ))))
                .andExpect(status().isCreated());
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", password
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asString();
    }

    private UUID createCustomer(String token, String name, String phone) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", name,
                                "phone", phone
                        ))))
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
                                        "applianceType", "Microwave",
                                        "brand", "Electrolux",
                                        "model", "MEF41",
                                        "voltage", "V110",
                                        "reportedIssue", "Not heating"
                                ))
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private record RegisteredShop(UUID tenantId, String token) {
    }
}
