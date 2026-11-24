package com.benchos.api.order;

import com.benchos.api.TestcontainersConfiguration;
import com.benchos.api.finance.internal.job.PendingPayablesAlertJob;
import com.benchos.api.iam.PushTokenQuery;
import com.benchos.api.order.internal.job.OrderDeadlineAlertJob;
import com.benchos.api.shared.push.PushDeliveryResult;
import com.benchos.api.shared.push.PushMessage;
import com.benchos.api.shared.push.PushNotificationSender;
import com.benchos.api.shared.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PushNotificationsIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PushTokenQuery pushTokenQuery;

    @Autowired
    private PendingPayablesAlertJob pendingPayablesAlertJob;

    @Autowired
    private OrderDeadlineAlertJob orderDeadlineAlertJob;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private PushNotificationSender pushNotificationSender;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        cleanDatabase();
        clearInvocations(pushNotificationSender);
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    private void cleanDatabase() {
        TenantContext.clear();
        jdbcTemplate.execute("DELETE FROM event_publication");
        jdbcTemplate.execute("DELETE FROM financial_transactions");
        jdbcTemplate.execute("DELETE FROM suppliers");
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

    @Test
    void registersReassignsAndUnregistersPushTokensWithTenantIsolation() throws Exception {
        RegisteredShop shopA = registerShop("Oficina Push A", "11111111000101", "11999991111", "Dono A", "pusha@shop.com");
        RegisteredShop shopB = registerShop("Oficina Push B", "22222222000102", "11999992222", "Dono B", "pushb@shop.com");

        // Invalid token format -> 422
        mockMvc.perform(post("/api/v1/users/me/push-tokens")
                        .header("Authorization", "Bearer " + shopA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "expoPushToken", "not-an-expo-token",
                                "platform", "ANDROID"
                        ))))
                .andExpect(status().is(422));

        String sharedPhoneToken = "ExponentPushToken[shared-device-001]";

        // Register on Shop A
        mockMvc.perform(post("/api/v1/users/me/push-tokens")
                        .header("Authorization", "Bearer " + shopA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "expoPushToken", sharedPhoneToken,
                                "platform", "ANDROID",
                                "deviceName", "Moto G54"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expoPushToken").value(sharedPhoneToken))
                .andExpect(jsonPath("$.platform").value("ANDROID"));

        assertThat(pushTokenQuery.findActiveTokensForTenant(shopA.tenantId())).containsExactly(sharedPhoneToken);

        // Same physical phone switches to Shop B -> cleanly detached from Shop A
        mockMvc.perform(post("/api/v1/users/me/push-tokens")
                        .header("Authorization", "Bearer " + shopB.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "expoPushToken", sharedPhoneToken,
                                "platform", "ANDROID",
                                "deviceName", "Moto G54"
                        ))))
                .andExpect(status().isOk());

        assertThat(pushTokenQuery.findActiveTokensForTenant(shopA.tenantId())).isEmpty();
        assertThat(pushTokenQuery.findActiveTokensForTenant(shopB.tenantId())).containsExactly(sharedPhoneToken);

        // Shop A cannot unregister Shop B's token
        mockMvc.perform(delete("/api/v1/users/me/push-tokens")
                        .header("Authorization", "Bearer " + shopA.token())
                        .param("token", sharedPhoneToken))
                .andExpect(status().isNoContent());
        assertThat(pushTokenQuery.findActiveTokensForTenant(shopB.tenantId())).containsExactly(sharedPhoneToken);

        // Shop B unregisters its own token
        mockMvc.perform(delete("/api/v1/users/me/push-tokens")
                        .header("Authorization", "Bearer " + shopB.token())
                        .param("token", sharedPhoneToken))
                .andExpect(status().isNoContent());
        assertThat(pushTokenQuery.findActiveTokensForTenant(shopB.tenantId())).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void dispatchesAsyncPushOnPublicQuoteApprovalAndPrunesInvalidTokens() throws Exception {
        RegisteredShop shopA = registerShop("Oficina Bancada", "33333333000103", "11999993333", "Dono Bancada", "bancada@shop.com");
        RegisteredShop shopB = registerShop("Outra Oficina", "44444444000104", "11999994444", "Dono B", "outra@shop.com");

        String ownerTokenA = "ExponentPushToken[shopA-owner]";
        String staleTokenA = "ExponentPushToken[shopA-stale]";
        String otherShopToken = "ExponentPushToken[shopB-owner]";

        registerPushToken(shopA.token(), ownerTokenA, "ANDROID");
        registerPushToken(shopB.token(), otherShopToken, "ANDROID");

        // Second active technician on Shop A whose token will be reported as DeviceNotRegistered
        String techJwt = createAndLoginUser(shopA.token(), "Tecnico A", "teca@shop.com", "TECHNICIAN");
        registerPushToken(techJwt, staleTokenA, "ANDROID");

        UUID customerId = createCustomer(shopA.token(), "Maria Silva", "11988887777");
        CreatedOrder order = createOrderWithQuotedItem(shopA.token(), customerId);

        doReturn(new PushDeliveryResult(1, 1, List.of(staleTokenA)))
                .when(pushNotificationSender).sendBatch(anyList());

        // Customer approves quote via public tracking endpoint
        mockMvc.perform(post("/api/v1/public/orders/{token}/items/1/approve", order.publicToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("APPROVED"));

        ArgumentCaptor<List<PushMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(pushNotificationSender, timeout(5000).times(1)).sendBatch(captor.capture());

        List<PushMessage> dispatched = captor.getValue();
        assertThat(dispatched)
                .extracting(PushMessage::toToken)
                .containsExactlyInAnyOrder(ownerTokenA, staleTokenA)
                .doesNotContain(otherShopToken);
        assertThat(dispatched.get(0).title()).contains("Orçamento aprovado");
        assertThat(dispatched.get(0).body()).contains("Maria Silva").contains("Micro-ondas Brastemp");
        assertThat(dispatched.get(0).data().get("route"))
                .isEqualTo("/orders/" + order.orderId() + "/items/" + order.itemId());

        // Stale token returned as DeviceNotRegistered was automatically pruned
        long deadline = System.currentTimeMillis() + 3000;
        while (pushTokenQuery.findActiveTokensForTenant(shopA.tenantId()).contains(staleTokenA)
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertThat(pushTokenQuery.findActiveTokensForTenant(shopA.tenantId())).containsExactly(ownerTokenA);
    }

    @Test
    @SuppressWarnings("unchecked")
    void dailyAlertJobsNotifyOnlyOwnersForDueStoreCreditAndOrderDeadlines() throws Exception {
        RegisteredShop shop = registerShop("Oficina Alertas", "55555555000105", "11999995555", "Dono Alertas", "alertas@shop.com");
        String ownerPushToken = "ExponentPushToken[alerts-owner]";
        String techPushToken = "ExponentPushToken[alerts-tech]";

        registerPushToken(shop.token(), ownerPushToken, "ANDROID");
        String techJwt = createAndLoginUser(shop.token(), "Tecnico", "tech-alerts@shop.com", "TECHNICIAN");
        registerPushToken(techJwt, techPushToken, "ANDROID");

        // 1. Create supplier + overdue and due-today STORE_CREDIT payables
        UUID supplierId = createSupplier(shop.token(), "Peças Brasil");
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        createStoreCreditExpense(shop.token(), supplierId, "Magnetron", "150.00", today.minusDays(2));
        createStoreCreditExpense(shop.token(), supplierId, "Capacitor", "80.00", today);

        // 2. Create an approved order item whose estimatedCompletionDate is today + 3 days
        UUID customerId = createCustomer(shop.token(), "João Lima", "11977776666");
        CreatedOrder order = createOrderWithQuotedItem(shop.token(), customerId);
        mockMvc.perform(post("/api/v1/orders/{orderId}/items/{itemId}/status", order.orderId(), order.itemId())
                        .header("Authorization", "Bearer " + shop.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "APPROVED"))))
                .andExpect(status().isOk());

        clearInvocations(pushNotificationSender);

        // Run pending payables alert job for today
        int financeNotified = pendingPayablesAlertJob.dispatchDueAlerts(today);
        assertThat(financeNotified).isEqualTo(1);

        ArgumentCaptor<List<PushMessage>> financeCaptor = ArgumentCaptor.forClass(List.class);
        verify(pushNotificationSender).sendBatch(financeCaptor.capture());
        assertThat(financeCaptor.getValue())
                .extracting(PushMessage::toToken)
                .containsExactly(ownerPushToken);
        assertThat(financeCaptor.getValue().get(0).body()).contains("2 contas pendentes");
        assertThat(financeCaptor.getValue().get(0).data().get("route")).isEqualTo("/(tabs)/finance");

        clearInvocations(pushNotificationSender);

        // Run order deadline alert job on reference date = today + 3 days (item due that day)
        int orderNotified = orderDeadlineAlertJob.dispatchDeadlineAlerts(today.plusDays(3));
        assertThat(orderNotified).isEqualTo(1);

        ArgumentCaptor<List<PushMessage>> orderCaptor = ArgumentCaptor.forClass(List.class);
        verify(pushNotificationSender).sendBatch(orderCaptor.capture());
        assertThat(orderCaptor.getValue())
                .extracting(PushMessage::toToken)
                .containsExactly(ownerPushToken);
        assertThat(orderCaptor.getValue().get(0).data().get("route")).isEqualTo("/(tabs)/workbench");
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

    private String createAndLoginUser(String ownerToken, String name, String email, String role) throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", name,
                                "email", email,
                                "password", "TechPass123!",
                                "role", role
                        ))))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "TechPass123!"
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("accessToken").asString();
    }

    private void registerPushToken(String jwt, String expoPushToken, String platform) throws Exception {
        mockMvc.perform(post("/api/v1/users/me/push-tokens")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "expoPushToken", expoPushToken,
                                "platform", platform
                        ))))
                .andExpect(status().isOk());
    }

    private UUID createCustomer(String jwt, String name, String phone) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", name,
                                "phone", phone
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asString());
    }

    private CreatedOrder createOrderWithQuotedItem(String jwt, UUID customerId) throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "customerId", customerId.toString(),
                                "items", List.of(Map.of(
                                        "applianceType", "Micro-ondas",
                                        "brand", "Brastemp",
                                        "voltage", "V110",
                                        "reportedIssue", "Não aquece"
                                ))
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(createResult.getResponse().getContentAsString());
        UUID orderId = UUID.fromString(json.get("id").asString());
        UUID publicToken = UUID.fromString(json.get("publicToken").asString());
        UUID itemId = UUID.fromString(json.get("items").get(0).get("id").asString());

        // Move RECEIVED -> DIAGNOSING -> set quote -> QUOTE_SENT
        mockMvc.perform(post("/api/v1/orders/{orderId}/items/{itemId}/status", orderId, itemId)
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "DIAGNOSING"))))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/orders/{orderId}/items/{itemId}/quote", orderId, itemId)
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "technicalDiagnosis", "Magnetron queimado",
                                "laborCost", 120.00,
                                "partsCost", 80.00,
                                "discount", 0.00
                        ))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/orders/{orderId}/items/{itemId}/status", orderId, itemId)
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "QUOTE_SENT"))))
                .andExpect(status().isOk());

        return new CreatedOrder(orderId, itemId, publicToken);
    }

    private UUID createSupplier(String jwt, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/finance/suppliers")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", name))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asString());
    }

    private void createStoreCreditExpense(
            String jwt, UUID supplierId, String description, String amount, LocalDate dueDate
    ) throws Exception {
        mockMvc.perform(post("/api/v1/finance/transactions")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "type", "EXPENSE",
                                "category", "PARTS_PURCHASE",
                                "supplierId", supplierId.toString(),
                                "description", description,
                                "amount", amount,
                                "paymentMethod", "STORE_CREDIT",
                                "dueDate", dueDate.toString()
                        ))))
                .andExpect(status().isCreated());
    }

    private record RegisteredShop(UUID tenantId, String token) {
    }

    private record CreatedOrder(UUID orderId, UUID itemId, UUID publicToken) {
    }
}
