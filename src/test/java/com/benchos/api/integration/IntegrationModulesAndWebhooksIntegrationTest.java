package com.benchos.api.integration;

import com.benchos.api.TestcontainersConfiguration;
import com.benchos.api.integration.internal.entity.WebhookDeliveryStatus;
import com.benchos.api.integration.internal.service.TenantModuleService;
import com.benchos.api.integration.internal.service.WebhookService;
import com.benchos.api.integration.internal.service.WebhookSigner;
import com.benchos.api.integration.internal.web.dto.CreateWebhookEndpointRequest;
import com.benchos.api.integration.internal.web.dto.UpdateWebhookEndpointRequest;
import com.benchos.api.integration.internal.web.dto.UpsertTenantModuleRequest;
import com.benchos.api.integration.internal.web.dto.WebhookDeliveryResponse;
import com.benchos.api.integration.internal.web.dto.WebhookEndpointResponse;
import com.benchos.api.shared.tenant.TenantContext;
import com.sun.net.httpserver.HttpServer;
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

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class IntegrationModulesAndWebhooksIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TenantModuleService tenantModuleService;

    @Autowired
    private WebhookService webhookService;

    @Autowired
    private WebhookSigner webhookSigner;

    private MockMvc mockMvc;
    private HttpServer webhookServer;
    private int webhookPort;
    private final List<CapturedRequest> capturedRequests = new CopyOnWriteArrayList<>();
    private final AtomicInteger serverResponseStatus = new AtomicInteger(200);

    private record CapturedRequest(
            String eventHeader,
            String deliveryHeader,
            String timestampHeader,
            String signatureHeader,
            String body
    ) {
    }

    private record RegisteredShop(UUID tenantId, String accessToken) {
    }

    @BeforeEach
    void setUp() throws Exception {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        cleanDatabase();
        capturedRequests.clear();
        serverResponseStatus.set(200);

        webhookServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        webhookPort = webhookServer.getAddress().getPort();
        webhookServer.createContext("/hook", exchange -> {
            byte[] bytes = exchange.getRequestBody().readAllBytes();
            String body = new String(bytes, StandardCharsets.UTF_8);
            capturedRequests.add(new CapturedRequest(
                    exchange.getRequestHeaders().getFirst(WebhookService.HEADER_EVENT),
                    exchange.getRequestHeaders().getFirst(WebhookService.HEADER_DELIVERY),
                    exchange.getRequestHeaders().getFirst(WebhookService.HEADER_TIMESTAMP),
                    exchange.getRequestHeaders().getFirst(WebhookService.HEADER_SIGNATURE),
                    body
            ));
            int code = serverResponseStatus.get();
            byte[] reply = (code >= 200 && code < 300 ? "{\"ok\":true}" : "downstream error")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(code, reply.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(reply);
            }
        });
        webhookServer.start();
    }

    @AfterEach
    void tearDown() {
        if (webhookServer != null) {
            webhookServer.stop(0);
        }
        cleanDatabase();
    }

    private void cleanDatabase() {
        TenantContext.clear();
        jdbcTemplate.execute("DELETE FROM event_publication");
        jdbcTemplate.execute("DELETE FROM webhook_deliveries");
        jdbcTemplate.execute("DELETE FROM webhook_endpoints");
        jdbcTemplate.execute("DELETE FROM tenant_modules");
        jdbcTemplate.execute("DELETE FROM financial_transactions");
        jdbcTemplate.execute("DELETE FROM part_requests");
        jdbcTemplate.execute("DELETE FROM service_order_item_quote_options");
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
    void tenantApiExposesModulesReadOnlyAndPreventsSelfServiceMutations() throws Exception {
        RegisteredShop shopA = registerShop("Oficina A", "11222333000181", "11999990001", "Dono A", "a@oficina.com");
        RegisteredShop shopB = registerShop("Oficina B", "22333444000192", "11999990002", "Dono B", "b@oficina.com");

        // Shop owners cannot self-provision modules or webhooks via public tenant API
        mockMvc.perform(put("/api/v1/integrations/modules/counter_tv_board")
                        .header("Authorization", "Bearer " + shopA.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("enabled", true))))
                .andExpect(status().is4xxClientError());

        mockMvc.perform(post("/api/v1/integrations/webhooks")
                        .header("Authorization", "Bearer " + shopA.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Self Hook",
                                "targetUrl", "http://127.0.0.1:" + webhookPort + "/hook",
                                "events", List.of("order.created")
                        ))))
                .andExpect(status().is4xxClientError());

        // Platform/super-admin enables counter_tv_board and a custom module for shop A
        tenantModuleService.upsertForTenant(
                shopA.tenantId(),
                "counter_tv_board",
                new UpsertTenantModuleRequest(true, Map.of("refreshSeconds", 15, "theme", "dark"))
        );
        tenantModuleService.upsertForTenant(
                shopA.tenantId(),
                "custom_n8n_bridge",
                new UpsertTenantModuleRequest(true, Map.of("channel", "whatsapp-vip"))
        );

        // Shop A sees its enabled modules via read-only GET /api/v1/integrations/modules
        MvcResult resA = mockMvc.perform(get("/api/v1/integrations/modules")
                        .header("Authorization", "Bearer " + shopA.accessToken()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode modulesA = objectMapper.readTree(resA.getResponse().getContentAsString());
        assertThat(findModule(modulesA, "counter_tv_board").get("enabled").asBoolean()).isTrue();
        assertThat(findModule(modulesA, "counter_tv_board").get("config").get("refreshSeconds").asInt()).isEqualTo(15);
        assertThat(findModule(modulesA, "custom_n8n_bridge").get("enabled").asBoolean()).isTrue();

        // Shop B remains completely isolated
        MvcResult resB = mockMvc.perform(get("/api/v1/integrations/modules")
                        .header("Authorization", "Bearer " + shopB.accessToken()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode modulesB = objectMapper.readTree(resB.getResponse().getContentAsString());
        assertThat(findModule(modulesB, "counter_tv_board").get("enabled").asBoolean()).isFalse();
        assertThat(findModuleOrNull(modulesB, "custom_n8n_bridge")).isNull();
    }

    @Test
    void platformProvisionedWebhookReceivesSignedAsyncOrderEventsAndSupportsRetryAndRotation() throws Exception {
        RegisteredShop shop = registerShop("Oficina Hook", "33444555000103", "11988887777", "Dono Hook", "hook@oficina.com");
        String targetUrl = "http://127.0.0.1:" + webhookPort + "/hook";

        // 1. Platform provisions webhook endpoint for the shop
        WebhookEndpointResponse created = webhookService.createEndpointForTenant(
                shop.tenantId(),
                new CreateWebhookEndpointRequest(
                        "Automação VIP n8n",
                        targetUrl,
                        List.of("order.created", "item.ready_for_pickup"),
                        true
                )
        );
        assertThat(created.signingSecret()).startsWith("whsec_");
        assertThat(webhookService.listEndpointsForTenant(shop.tenantId()).getFirst().signingSecret()).isNull();

        // 2. Test ping + HMAC-SHA256 verification
        WebhookDeliveryResponse testDelivery = webhookService.sendTestEventForTenant(shop.tenantId(), created.id());
        assertThat(testDelivery.status()).isEqualTo(WebhookDeliveryStatus.SUCCESS);
        assertThat(testDelivery.httpStatus()).isEqualTo(200);

        assertThat(capturedRequests).hasSize(1);
        CapturedRequest testReq = capturedRequests.getFirst();
        String expectedSig = webhookSigner.computeSignatureHeader(
                created.signingSecret(),
                Long.parseLong(testReq.timestampHeader()),
                testReq.body()
        );
        assertThat(testReq.signatureHeader()).isEqualTo(expectedSig);

        // 3. Shop creates an order -> async ApplicationModuleListener dispatches order.created
        UUID customerId = createCustomer(shop.accessToken(), "Roberto Alves", "11977776666");
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + shop.accessToken())
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
                .andExpect(status().isCreated());

        long deadline = System.currentTimeMillis() + 4000L;
        while (System.currentTimeMillis() < deadline
                && capturedRequests.stream().noneMatch(r -> "order.created".equals(r.eventHeader()))) {
            Thread.sleep(50);
        }
        CapturedRequest orderCreatedReq = capturedRequests.stream()
                .filter(r -> "order.created".equals(r.eventHeader()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected async order.created webhook delivery"));

        JsonNode payload = objectMapper.readTree(orderCreatedReq.body());
        assertThat(payload.get("event").asString()).isEqualTo("order.created");
        assertThat(payload.get("data").get("customerName").asString()).isEqualTo("Roberto Alves");

        // 4. Failure + retry + secret rotation + pause + delete
        serverResponseStatus.set(500);
        WebhookDeliveryResponse failed = webhookService.sendTestEventForTenant(shop.tenantId(), created.id());
        assertThat(failed.status()).isEqualTo(WebhookDeliveryStatus.FAILED);
        assertThat(failed.httpStatus()).isEqualTo(500);

        serverResponseStatus.set(200);
        WebhookDeliveryResponse retried = webhookService.retryDeliveryForTenant(shop.tenantId(), created.id(), failed.id());
        assertThat(retried.status()).isEqualTo(WebhookDeliveryStatus.SUCCESS);
        assertThat(retried.attemptCount()).isEqualTo(2);

        WebhookEndpointResponse rotated = webhookService.rotateSecretForTenant(shop.tenantId(), created.id());
        assertThat(rotated.signingSecret()).isNotEqualTo(created.signingSecret());

        WebhookEndpointResponse paused = webhookService.updateEndpointForTenant(
                shop.tenantId(),
                created.id(),
                new UpdateWebhookEndpointRequest(null, null, null, false)
        );
        assertThat(paused.active()).isFalse();
        assertThat(webhookService.listDeliveriesForTenant(shop.tenantId(), created.id(), 20)).hasSize(3);

        webhookService.deleteEndpointForTenant(shop.tenantId(), created.id());
        assertThat(webhookService.listEndpointsForTenant(shop.tenantId())).isEmpty();
    }

    private static JsonNode findModule(JsonNode arrayNode, String moduleKey) {
        JsonNode found = findModuleOrNull(arrayNode, moduleKey);
        assertThat(found).as("Expected module '%s' in %s", moduleKey, arrayNode).isNotNull();
        return found;
    }

    private static JsonNode findModuleOrNull(JsonNode arrayNode, String moduleKey) {
        for (JsonNode node : arrayNode) {
            if (moduleKey.equals(node.get("moduleKey").asString())) {
                return node;
            }
        }
        return null;
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
                                "password", "SenhaForte@123"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        UUID tenantId = UUID.fromString(root.get("tenant").get("id").asString());
        return new RegisteredShop(tenantId, root.get("accessToken").asString());
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
}
