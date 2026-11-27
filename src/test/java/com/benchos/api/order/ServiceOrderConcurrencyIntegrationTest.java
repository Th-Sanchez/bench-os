package com.benchos.api.order;

import com.benchos.api.TestcontainersConfiguration;
import com.benchos.api.order.internal.entity.ServiceOrderItem;
import com.benchos.api.order.internal.repository.ServiceOrderItemRepository;
import com.benchos.api.shared.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ServiceOrderConcurrencyIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ServiceOrderItemRepository serviceOrderItemRepository;

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
        jdbcTemplate.execute("DELETE FROM item_photos");
        jdbcTemplate.execute("DELETE FROM service_order_items");
        jdbcTemplate.execute("DELETE FROM service_orders");
        jdbcTemplate.execute("DELETE FROM tenant_sequences");
        jdbcTemplate.execute("DELETE FROM customers");
        jdbcTemplate.execute("DELETE FROM user_push_tokens");
        jdbcTemplate.execute("DELETE FROM password_reset_tokens");
        jdbcTemplate.execute("DELETE FROM users");
        jdbcTemplate.execute("DELETE FROM tenant_settings");
        jdbcTemplate.execute("DELETE FROM tenants");
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

    @Test
    void concurrentPublicApproveAndRejectAllowsExactlyOneWinnerAndSingleEvent() throws Exception {
        RegisteredShop shop = registerShop("Oficina Concorrencia", "11222333000144", "11988881111", "Carlos", "carlos@conc.com");
        UUID customerId = createCustomer(shop.token(), "Maria Silva", "11977772222");
        QuotedOrder quoted = createQuotedOrder(shop.token(), customerId);

        long versionBefore = queryItemVersion(quoted.itemId());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Integer> approveFuture = pool.submit(() -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return mockMvc.perform(post("/api/v1/public/orders/{token}/items/1/approve", quoted.publicToken()))
                        .andReturn()
                        .getResponse()
                        .getStatus();
            });

            Future<Integer> rejectFuture = pool.submit(() -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return mockMvc.perform(post("/api/v1/public/orders/{token}/items/1/reject", quoted.publicToken()))
                        .andReturn()
                        .getResponse()
                        .getStatus();
            });

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            int approveStatus = approveFuture.get(10, TimeUnit.SECONDS);
            int rejectStatus = rejectFuture.get(10, TimeUnit.SECONDS);

            // Exactly one succeeds (200) and the other is deterministically rejected (422 or 409)
            assertThat(List.of(approveStatus, rejectStatus)).contains(200);
            int loserStatus = approveStatus == 200 ? rejectStatus : approveStatus;
            assertThat(loserStatus).isIn(409, 422);

            String expectedFinalStatus = approveStatus == 200 ? "APPROVED" : "REJECTED";
            assertThat(queryItemStatus(quoted.itemId())).isEqualTo(expectedFinalStatus);
            assertThat(queryItemVersion(quoted.itemId())).isEqualTo(versionBefore + 1);

            // Exactly ONE PublicQuoteRespondedEvent published (per listener — no contradictory push/webhook notifications)
            Integer distinctQuoteEvents = jdbcTemplate.queryForObject(
                    "SELECT COUNT(DISTINCT serialized_event) FROM event_publication WHERE event_type LIKE '%PublicQuoteRespondedEvent'",
                    Integer.class
            );
            Integer pushListenerEvents = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM event_publication WHERE event_type LIKE '%PublicQuoteRespondedEvent' AND listener_id LIKE '%PublicQuotePushNotificationListener%'",
                    Integer.class
            );
            assertThat(distinctQuoteEvents).isEqualTo(1);
            assertThat(pushListenerEvents).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentPublicApproveAndStaffRejectAllowsExactlyOneWinner() throws Exception {
        RegisteredShop shop = registerShop("Oficina Mista", "22333444000155", "11988882222", "Paulo", "paulo@mista.com");
        UUID customerId = createCustomer(shop.token(), "Joao Souza", "11977773333");
        QuotedOrder quoted = createQuotedOrder(shop.token(), customerId);

        long versionBefore = queryItemVersion(quoted.itemId());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Integer> publicApprove = pool.submit(() -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return mockMvc.perform(post("/api/v1/public/orders/{token}/items/1/approve", quoted.publicToken()))
                        .andReturn()
                        .getResponse()
                        .getStatus();
            });

            Future<Integer> staffReject = pool.submit(() -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return mockMvc.perform(post("/api/v1/orders/{orderId}/items/{itemId}/status", quoted.orderId(), quoted.itemId())
                                .header("Authorization", "Bearer " + shop.token())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of("status", "REJECTED"))))
                        .andReturn()
                        .getResponse()
                        .getStatus();
            });

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            int approveStatus = publicApprove.get(10, TimeUnit.SECONDS);
            int rejectStatus = staffReject.get(10, TimeUnit.SECONDS);

            assertThat(List.of(approveStatus, rejectStatus)).contains(200);
            int loserStatus = approveStatus == 200 ? rejectStatus : approveStatus;
            assertThat(loserStatus).isIn(409, 422);

            String expectedFinalStatus = approveStatus == 200 ? "APPROVED" : "REJECTED";
            assertThat(queryItemStatus(quoted.itemId())).isEqualTo(expectedFinalStatus);
            assertThat(queryItemVersion(quoted.itemId())).isEqualTo(versionBefore + 1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentDoubleDeliveryPreventsDuplicateFinancialTransactions() throws Exception {
        RegisteredShop shop = registerShop("Oficina Caixa", "33444555000166", "11988883333", "Marcos", "marcos@caixa.com");
        UUID customerId = createCustomer(shop.token(), "Clara Lima", "11977774444");
        QuotedOrder quoted = createQuotedOrder(shop.token(), customerId);

        // Advance to READY_FOR_PICKUP
        transition(shop.token(), quoted.orderId(), quoted.itemId(), "APPROVED");
        transition(shop.token(), quoted.orderId(), quoted.itemId(), "IN_REPAIR");
        transition(shop.token(), quoted.orderId(), quoted.itemId(), "READY_FOR_PICKUP");

        String deliverPayload = objectMapper.writeValueAsString(Map.of(
                "payments", List.of(Map.of("paymentMethod", "PIX", "amount", new BigDecimal("200.00")))
        ));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Integer> deliverA = pool.submit(() -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return mockMvc.perform(post("/api/v1/orders/{orderId}/items/{itemId}/deliver", quoted.orderId(), quoted.itemId())
                                .header("Authorization", "Bearer " + shop.token())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(deliverPayload))
                        .andReturn()
                        .getResponse()
                        .getStatus();
            });

            Future<Integer> deliverB = pool.submit(() -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return mockMvc.perform(post("/api/v1/orders/{orderId}/items/{itemId}/deliver", quoted.orderId(), quoted.itemId())
                                .header("Authorization", "Bearer " + shop.token())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(deliverPayload))
                        .andReturn()
                        .getResponse()
                        .getStatus();
            });

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            int statusA = deliverA.get(10, TimeUnit.SECONDS);
            int statusB = deliverB.get(10, TimeUnit.SECONDS);

            assertThat(List.of(statusA, statusB)).containsExactlyInAnyOrder(200, 422);
            assertThat(queryItemStatus(quoted.itemId())).isEqualTo("DELIVERED");

            awaitPendingEventPublications();

            // Exactly ONE financial transaction of R$ 200,00 (no double-charging)
            Integer txCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM financial_transactions WHERE service_order_item_id = ?",
                    Integer.class,
                    quoted.itemId()
            );
            assertThat(txCount).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void optimisticLockVersionPreventsStaleOverwrite() throws Exception {
        RegisteredShop shop = registerShop("Oficina Version", "44555666000177", "11988884444", "Lia", "lia@version.com");
        UUID customerId = createCustomer(shop.token(), "Bruno Alves", "11977775555");
        QuotedOrder quoted = createQuotedOrder(shop.token(), customerId);

        // Load detached snapshot at version V (with serviceOrder initialized)
        ServiceOrderItem staleCopy = transactionTemplate.execute(status ->
                serviceOrderItemRepository.findByIdAndServiceOrderIdAndTenantId(
                        quoted.itemId(),
                        quoted.orderId(),
                        shop.tenantId()
                ).orElseThrow()
        );
        assertThat(staleCopy).isNotNull();

        // Advance version in DB via a separate transaction (public approval)
        mockMvc.perform(post("/api/v1/public/orders/{token}/items/1/approve", quoted.publicToken()))
                .andExpect(status().isOk());

        // Attempting to persist stale detached entity must fail with OptimisticLockingFailureException
        staleCopy.transitionTo(ItemStatus.REJECTED, null, null, Instant.now());
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status ->
                serviceOrderItemRepository.saveAndFlush(staleCopy)
        )).isInstanceOf(ObjectOptimisticLockingFailureException.class);

        // Committed APPROVED state remains intact
        assertThat(queryItemStatus(quoted.itemId())).isEqualTo("APPROVED");
    }

    private String queryItemStatus(UUID itemId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM service_order_items WHERE id = ?",
                String.class,
                itemId
        );
    }

    private long queryItemVersion(UUID itemId) {
        Long version = jdbcTemplate.queryForObject(
                "SELECT version FROM service_order_items WHERE id = ?",
                Long.class,
                itemId
        );
        return version == null ? -1L : version;
    }

    private RegisteredShop registerShop(
            String shopName, String document, String phone, String ownerName, String email
    ) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "shopName", shopName,
                                "document", document,
                                "phone", phone,
                                "ownerName", ownerName,
                                "email", email,
                                "password", "StrongPass123!"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return new RegisteredShop(
                UUID.fromString(json.get("tenant").get("id").asString()),
                json.get("accessToken").asString()
        );
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

    private QuotedOrder createQuotedOrder(String token, UUID customerId) throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "customerId", customerId.toString(),
                                "items", List.of(Map.of(
                                        "applianceType", "Micro-ondas",
                                        "brand", "Electrolux",
                                        "voltage", "V110",
                                        "reportedIssue", "Nao aquece"
                                ))
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(createResult.getResponse().getContentAsString());
        UUID orderId = UUID.fromString(json.get("id").asString());
        UUID publicToken = UUID.fromString(json.get("publicToken").asString());
        UUID itemId = UUID.fromString(json.get("items").get(0).get("id").asString());

        transition(token, orderId, itemId, "DIAGNOSING");

        mockMvc.perform(patch("/api/v1/orders/{orderId}/items/{itemId}/quote", orderId, itemId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "technicalDiagnosis", "Magnetron queimado",
                                "laborCost", new BigDecimal("120.00"),
                                "partsCost", new BigDecimal("80.00"),
                                "discount", BigDecimal.ZERO,
                                "warrantyDays", 90
                        ))))
                .andExpect(status().isOk());

        transition(token, orderId, itemId, "QUOTE_SENT");
        return new QuotedOrder(orderId, itemId, publicToken);
    }

    private void transition(String token, UUID orderId, UUID itemId, String status) throws Exception {
        mockMvc.perform(post("/api/v1/orders/{orderId}/items/{itemId}/status", orderId, itemId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", status))))
                .andExpect(status().isOk());
    }

    private record RegisteredShop(UUID tenantId, String token) {
    }

    private record QuotedOrder(UUID orderId, UUID itemId, UUID publicToken) {
    }
}
