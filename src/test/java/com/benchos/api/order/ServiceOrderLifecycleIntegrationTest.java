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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
class ServiceOrderLifecycleIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationEvents applicationEvents;

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
    void executesFullMultiItemOrderLifecycleSplitPaymentWhatsappLinkAndPublicTracking() throws Exception {
        RegisteredShop shopA = registerShop("Oficina do Pai", "12345678000101", "11999990001", "Carlos", "carlos@pai.com", "Secret123!");
        RegisteredShop shopB = registerShop("Oficina do Primo", "98765432000102", "11999990002", "Marcos", "marcos@primo.com", "Secret123!");

        // Customize Shop A settings (estimated days = 5, custom WhatsApp template with {tag})
        mockMvc.perform(put("/api/v1/tenant/settings")
                        .header("Authorization", "Bearer " + shopA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "defaultLaborCost", new BigDecimal("80.00"),
                                "defaultEstimatedDays", 5,
                                "abandonmentDays", 180,
                                "whatsappQuoteTemplate",
                                "Ola {customerName}! Orcamento do {appliance} (OS #{orderNumber}, etiqueta {tag}): {totalPrice}. Acompanhe: {trackingUrl}"
                        ))))
                .andExpect(status().isOk());

        UUID customerId = createCustomer(shopA.token(), "Maria Silva", "(11) 98888-1234");

        // Open order with 2 items: Microwave (will be approved & delivered with split payment) and Air Fryer (will be rejected & returned free)
        JsonNode order = createOrder(shopA.token(), customerId, "Internal counter note - do not expose", List.of(
                Map.of(
                        "applianceType", "Microwave",
                        "brand", "Electrolux",
                        "model", "MEF41",
                        "voltage", "V110",
                        "reportedIssue", "Not heating"
                ),
                Map.of(
                        "applianceType", "Air Fryer",
                        "brand", "Mondial",
                        "model", "AF-30",
                        "voltage", "V220",
                        "reportedIssue", "Not powering on"
                )
        ));

        UUID orderId = UUID.fromString(order.get("id").asString());
        UUID publicToken = UUID.fromString(order.get("publicToken").asString());
        UUID item1Id = UUID.fromString(order.get("items").get(0).get("id").asString());
        UUID item2Id = UUID.fromString(order.get("items").get(1).get("id").asString());

        // --- ITEM 1: RECEIVED -> DIAGNOSING -> QUOTE -> QUOTE_SENT -> APPROVED ---
        transition(shopA.token(), orderId, item1Id, "DIAGNOSING", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DIAGNOSING"));

        updateQuote(shopA.token(), orderId, item1Id, "Magnetron defective", "100.00", "150.00", "20.00", 90)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPrice").value(230.00))
                .andExpect(jsonPath("$.technicalDiagnosis").value("Magnetron defective"));

        transition(shopA.token(), orderId, item1Id, "QUOTE_SENT", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUOTE_SENT"));

        // Generate WhatsApp link for Item 1
        MvcResult waResult = mockMvc.perform(get("/api/v1/orders/{orderId}/items/{itemId}/whatsapp-link", orderId, item1Id)
                        .header("Authorization", "Bearer " + shopA.token()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode waJson = objectMapper.readTree(waResult.getResponse().getContentAsString());
        String expectedTrackingUrl = "http://localhost:8080/track/" + publicToken;
        assertThat(waJson.get("message").asString())
                .contains("Maria Silva")
                .contains("Microwave Electrolux MEF41")
                .contains("OS #1001")
                .contains("1001-1")
                .contains("R$ 230,00")
                .contains(expectedTrackingUrl);
        assertThat(waJson.get("whatsappUrl").asString())
                .startsWith("https://wa.me/5511988881234?text=");

        // Cross-tenant WhatsApp link returns 404
        mockMvc.perform(get("/api/v1/orders/{orderId}/items/{itemId}/whatsapp-link", orderId, item1Id)
                        .header("Authorization", "Bearer " + shopB.token()))
                .andExpect(status().isNotFound());

        // Approve Item 1 -> calculates estimatedCompletionDate = today + 5 days
        LocalDate expectedEstimate = LocalDate.now(ZoneOffset.UTC).plusDays(5);
        transition(shopA.token(), orderId, item1Id, "APPROVED", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.estimatedCompletionDate").value(expectedEstimate.toString()))
                .andExpect(jsonPath("$.approvedAt").isNotEmpty());

        // Price lock after APPROVED -> 422
        updateQuote(shopA.token(), orderId, item1Id, "Attempt edit", "120.00", "150.00", "0.00", 90)
                .andExpect(status().is(422));

        // Explicit reopen APPROVED -> QUOTE_SENT allows editing quote
        transition(shopA.token(), orderId, item1Id, "QUOTE_SENT", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUOTE_SENT"));

        updateQuote(shopA.token(), orderId, item1Id, "Magnetron + fuse", "100.00", "160.00", "10.00", 120)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPrice").value(250.00))
                .andExpect(jsonPath("$.warrantyDays").value(120));

        // Re-approve -> WAITING_PARTS -> IN_REPAIR -> READY_FOR_PICKUP
        transition(shopA.token(), orderId, item1Id, "APPROVED", null).andExpect(status().isOk());
        transition(shopA.token(), orderId, item1Id, "WAITING_PARTS", null).andExpect(status().isOk());
        transition(shopA.token(), orderId, item1Id, "IN_REPAIR", null).andExpect(status().isOk());
        transition(shopA.token(), orderId, item1Id, "READY_FOR_PICKUP", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finishedAt").isNotEmpty());

        // Deliver with mismatched split payment sum -> 422
        mockMvc.perform(post("/api/v1/orders/{orderId}/items/{itemId}/deliver", orderId, item1Id)
                        .header("Authorization", "Bearer " + shopA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "payments", List.of(
                                        Map.of("paymentMethod", "PIX", "amount", new BigDecimal("100.00")),
                                        Map.of("paymentMethod", "CREDIT_CARD", "amount", new BigDecimal("100.00"))
                                )
                        ))))
                .andExpect(status().is(422));

        // Deliver Item 1 with exact split payment (100.00 PIX + 150.00 CREDIT_CARD = 250.00)
        mockMvc.perform(post("/api/v1/orders/{orderId}/items/{itemId}/deliver", orderId, item1Id)
                        .header("Authorization", "Bearer " + shopA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "payments", List.of(
                                        Map.of("paymentMethod", "PIX", "amount", new BigDecimal("100.00")),
                                        Map.of("paymentMethod", "CREDIT_CARD", "amount", new BigDecimal("150.00"))
                                )
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.deliveredAt").isNotEmpty());

        // --- ITEM 2: RECEIVED -> DIAGNOSING -> QUOTE -> QUOTE_SENT -> REJECTED -> DELIVERED (free) ---
        transition(shopA.token(), orderId, item2Id, "DIAGNOSING", null).andExpect(status().isOk());
        updateQuote(shopA.token(), orderId, item2Id, "Thermal fuse + board", "90.00", "110.00", "0.00", 90)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPrice").value(200.00));
        transition(shopA.token(), orderId, item2Id, "QUOTE_SENT", null).andExpect(status().isOk());
        transition(shopA.token(), orderId, item2Id, "REJECTED", null).andExpect(status().isOk());

        // Deliver rejected Item 2 without body/payments -> zeros totalPrice and emits NO event
        mockMvc.perform(post("/api/v1/orders/{orderId}/items/{itemId}/deliver", orderId, item2Id)
                        .header("Authorization", "Bearer " + shopA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.totalPrice").value(0.00))
                .andExpect(jsonPath("$.deliveredAt").isNotEmpty());

        // Assert exactly ONE OrderItemDeliveredEvent was published (for Item 1 only!)
        List<OrderItemDeliveredEvent> deliveredEvents = applicationEvents.stream(OrderItemDeliveredEvent.class).toList();
        assertThat(deliveredEvents).hasSize(1);
        OrderItemDeliveredEvent event = deliveredEvents.getFirst();
        assertThat(event.tenantId()).isEqualTo(shopA.tenantId());
        assertThat(event.serviceOrderId()).isEqualTo(orderId);
        assertThat(event.itemId()).isEqualTo(item1Id);
        assertThat(event.orderNumber()).isEqualTo(1001L);
        assertThat(event.itemIndex()).isEqualTo(1);
        assertThat(event.totalPrice()).isEqualByComparingTo("250.00");
        assertThat(event.payments()).containsExactly(
                new PaymentPart(PaymentMethod.PIX, new BigDecimal("100.00")),
                new PaymentPart(PaymentMethod.CREDIT_CARD, new BigDecimal("150.00"))
        );

        // --- PUBLIC TRACKING ENDPOINT (NO JWT) ---
        MvcResult publicResult = mockMvc.perform(get("/api/v1/public/orders/{publicToken}", publicToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shopName").value("Oficina do Pai"))
                .andExpect(jsonPath("$.orderNumber").value(1001))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].tag").value("1001-1"))
                .andExpect(jsonPath("$.items[0].status").value("DELIVERED"))
                .andExpect(jsonPath("$.items[0].totalPrice").value(250.00))
                .andExpect(jsonPath("$.items[1].tag").value("1001-2"))
                .andExpect(jsonPath("$.items[1].status").value("DELIVERED"))
                .andExpect(jsonPath("$.items[1].totalPrice").value(0.00))
                .andReturn();

        // Sanitized view does not leak sensitive/internal fields
        String publicJson = publicResult.getResponse().getContentAsString();
        assertThat(publicJson)
                .doesNotContain("Maria Silva")
                .doesNotContain("11988881234")
                .doesNotContain("Internal counter note")
                .doesNotContain("laborCost")
                .doesNotContain("partsCost")
                .doesNotContain("tenantId");

        // Unknown public token returns 404
        mockMvc.perform(get("/api/v1/public/orders/{publicToken}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void enforcesAbandonmentDiscardPolicyAndCancellationRules() throws Exception {
        RegisteredShop shop = registerShop("Oficina Central", "11122233000199", "11988887777", "Paulo", "paulo@central.com", "MasterPass1!");
        UUID customerId = createCustomer(shop.token(), "Joao Lima", "(11) 97777-8888");

        JsonNode order = createOrder(shop.token(), customerId, null, List.of(
                Map.of("applianceType", "Blender", "brand", "Philco", "voltage", "BIVOLT", "reportedIssue", "Motor dead"),
                Map.of("applianceType", "Toaster", "brand", "Oster", "voltage", "V110", "reportedIssue", "Lever broken")
        ));
        UUID orderId = UUID.fromString(order.get("id").asString());
        UUID abandonItemId = UUID.fromString(order.get("items").get(0).get("id").asString());
        UUID cancelItemId = UUID.fromString(order.get("items").get(1).get("id").asString());

        // Move item 1 to READY_FOR_PICKUP
        transition(shop.token(), orderId, abandonItemId, "DIAGNOSING", null).andExpect(status().isOk());
        transition(shop.token(), orderId, abandonItemId, "QUOTE_SENT", null).andExpect(status().isOk());
        transition(shop.token(), orderId, abandonItemId, "APPROVED", null).andExpect(status().isOk());
        transition(shop.token(), orderId, abandonItemId, "IN_REPAIR", null).andExpect(status().isOk());
        transition(shop.token(), orderId, abandonItemId, "READY_FOR_PICKUP", null).andExpect(status().isOk());

        // Immediate discard -> 422 (must wait 180 days)
        transition(shop.token(), orderId, abandonItemId, "DISCARDED", null)
                .andExpect(status().is(422));

        // Not yet in abandonment-eligible query
        mockMvc.perform(get("/api/v1/order-items/abandonment-eligible")
                        .header("Authorization", "Bearer " + shop.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        // Simulate 181 days elapsed since finished_at
        Instant finished181DaysAgo = Instant.now().minus(Duration.ofDays(181));
        jdbcTemplate.update(
                "UPDATE service_order_items SET finished_at = ?, updated_at = ? WHERE id = ?",
                Timestamp.from(finished181DaysAgo),
                Timestamp.from(finished181DaysAgo),
                abandonItemId
        );

        // Now appears in abandonment-eligible query
        mockMvc.perform(get("/api/v1/order-items/abandonment-eligible")
                        .header("Authorization", "Bearer " + shop.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(abandonItemId.toString()));

        // Discard succeeds with discardedAt set and NO financial event
        transition(shop.token(), orderId, abandonItemId, "DISCARDED", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISCARDED"))
                .andExpect(jsonPath("$.discardedAt").isNotEmpty());

        assertThat(applicationEvents.stream(OrderItemDeliveredEvent.class).toList()).isEmpty();

        // Cancellation without reason -> 422; with reason -> 200 CANCELLED
        transition(shop.token(), orderId, cancelItemId, "CANCELLED", null)
                .andExpect(status().is(422));

        transition(shop.token(), orderId, cancelItemId, "CANCELLED", "Customer gave up before diagnosis")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason").value("Customer gave up before diagnosis"));

        // Invalid transition out of CANCELLED -> 422
        transition(shop.token(), orderId, cancelItemId, "DIAGNOSING", null)
                .andExpect(status().is(422));
    }

    @Test
    void allowsPublicWebTrackingPageAndCustomerQuoteApprovalOrRejectionWithoutAuth() throws Exception {
        RegisteredShop shop = registerShop(
                "Oficina Publica", "44555666000177", "11988887777",
                "Owner Public", "owner.public.approve@benchos.com", "StrongPass123!"
        );
        UUID customerId = createCustomer(shop.token(), "Joao Cliente", "11977776666");
        JsonNode order = createOrder(shop.token(), customerId, null, List.of(
                Map.of("applianceType", "Microwave", "brand", "Electrolux", "voltage", "V110", "reportedIssue", "Not heating"),
                Map.of("applianceType", "Blender", "brand", "Philco", "voltage", "V110", "reportedIssue", "Burnt smell")
        ));

        UUID orderId = UUID.fromString(order.get("id").asString());
        UUID publicToken = UUID.fromString(order.get("publicToken").asString());
        UUID item1Id = UUID.fromString(order.get("items").get(0).get("id").asString());
        UUID item2Id = UUID.fromString(order.get("items").get(1).get("id").asString());

        // Public HTML tracking page renders without authentication
        mockMvc.perform(get("/track/{publicToken}", publicToken))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Acompanhamento de reparo")));

        // Move both items to QUOTE_SENT
        transition(shop.token(), orderId, item1Id, "DIAGNOSING", null).andExpect(status().isOk());
        updateQuote(shop.token(), orderId, item1Id, "Capacitor", "80.00", "40.00", "0.00", 90).andExpect(status().isOk());
        transition(shop.token(), orderId, item1Id, "QUOTE_SENT", null).andExpect(status().isOk());

        transition(shop.token(), orderId, item2Id, "DIAGNOSING", null).andExpect(status().isOk());
        updateQuote(shop.token(), orderId, item2Id, "Motor burnt", "80.00", "90.00", "0.00", 90).andExpect(status().isOk());
        transition(shop.token(), orderId, item2Id, "QUOTE_SENT", null).andExpect(status().isOk());

        // Customer approves item 1 anonymously via public link
        mockMvc.perform(post("/api/v1/public/orders/{publicToken}/items/1/approve", publicToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shopPhone").value("11988887777"))
                .andExpect(jsonPath("$.items[0].status").value("APPROVED"))
                .andExpect(jsonPath("$.items[0].approvedAt").isNotEmpty())
                .andExpect(jsonPath("$.items[0].estimatedCompletionDate").isNotEmpty());

        // Duplicate decision on already-approved item -> 422
        mockMvc.perform(post("/api/v1/public/orders/{publicToken}/items/1/reject", publicToken))
                .andExpect(status().is(422));

        // Customer rejects item 2 anonymously via public link
        mockMvc.perform(post("/api/v1/public/orders/{publicToken}/items/2/reject", publicToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[1].status").value("REJECTED"));
    }

    @Test
    void blocksAttendantFromTechnicalQuoteAndStatusTransitionsWhileAllowingCounterFlow() throws Exception {
        RegisteredShop shop = registerShop(
                "Oficina RBAC", "55666777000188", "11966665555",
                "Owner Rbac", "owner.rbac.order@benchos.com", "StrongPass123!"
        );

        // Create ATTENDANT and TECHNICIAN users
        String attendantToken = createUserAndLogin(shop.token(), "Ana Atendente", "ana.rbac.order@benchos.com", "StrongPass123!", "ATTENDANT");
        String techToken = createUserAndLogin(shop.token(), "Lucas Tecnico", "lucas.rbac.order@benchos.com", "StrongPass123!", "TECHNICIAN");

        UUID customerId = createCustomer(attendantToken, "Carlos Cliente", "11955554444");

        // Attendant CAN create orders
        JsonNode order = createOrder(attendantToken, customerId, null, List.of(
                Map.of("applianceType", "Air Fryer", "brand", "Mondial", "voltage", "V110", "reportedIssue", "No heat")
        ));
        UUID orderId = UUID.fromString(order.get("id").asString());
        UUID itemId = UUID.fromString(order.get("items").get(0).get("id").asString());

        // Attendant CANNOT start diagnosis or edit quote -> 403
        transition(attendantToken, orderId, itemId, "DIAGNOSING", null).andExpect(status().isForbidden());

        // Technician CAN start diagnosis, update quote, and send quote
        transition(techToken, orderId, itemId, "DIAGNOSING", null).andExpect(status().isOk());
        updateQuote(attendantToken, orderId, itemId, "Should fail", "80.00", "20.00", "0.00", 90).andExpect(status().isForbidden());
        updateQuote(techToken, orderId, itemId, "Thermostat", "80.00", "20.00", "0.00", 90).andExpect(status().isOk());
        transition(attendantToken, orderId, itemId, "QUOTE_SENT", null).andExpect(status().isForbidden());
        transition(techToken, orderId, itemId, "QUOTE_SENT", null).andExpect(status().isOk());

        // Attendant CAN approve quote (e.g. customer confirmed by phone)
        transition(attendantToken, orderId, itemId, "APPROVED", null).andExpect(status().isOk());

        // Attendant CANNOT move to technical repair stages or reopen quote -> 403
        transition(attendantToken, orderId, itemId, "WAITING_PARTS", null).andExpect(status().isForbidden());
        transition(attendantToken, orderId, itemId, "IN_REPAIR", null).andExpect(status().isForbidden());
        transition(attendantToken, orderId, itemId, "QUOTE_SENT", null).andExpect(status().isForbidden());

        // Technician completes repair
        transition(techToken, orderId, itemId, "IN_REPAIR", null).andExpect(status().isOk());
        transition(attendantToken, orderId, itemId, "READY_FOR_PICKUP", null).andExpect(status().isForbidden());
        transition(techToken, orderId, itemId, "READY_FOR_PICKUP", null).andExpect(status().isOk());

        // Attendant CAN deliver and record payment
        mockMvc.perform(post("/api/v1/orders/{orderId}/items/{itemId}/deliver", orderId, itemId)
                        .header("Authorization", "Bearer " + attendantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "payments", List.of(Map.of("paymentMethod", "PIX", "amount", new BigDecimal("100.00")))
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));
    }

    @Test
    void generatesConsolidatedOrderWhatsappMessagesByStageAndManagesCustomApplianceTypes() throws Exception {
        RegisteredShop shop = registerShop(
                "Oficina Consolidada", "66777888000199", "11944443333",
                "Owner Consolidado", "owner.consolidated@benchos.com", "StrongPass123!"
        );

        // Add & verify custom appliance type on tenant settings
        mockMvc.perform(post("/api/v1/tenant/settings/appliance-types")
                        .header("Authorization", "Bearer " + shop.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Cafeteira Expresso"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customApplianceTypes[0]").value("Cafeteira Expresso"));

        // Configure 3 WhatsApp templates (INTAKE, QUOTE, READY)
        mockMvc.perform(put("/api/v1/tenant/settings")
                        .header("Authorization", "Bearer " + shop.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "defaultLaborCost", new BigDecimal("80.00"),
                                "defaultEstimatedDays", 3,
                                "abandonmentDays", 180,
                                "whatsappIntakeTemplate", "Entrada OS #{orderNumber} para {customerName}:\n{appliances}\n{trackingUrl}",
                                "whatsappQuoteTemplate", "Orcamento OS #{orderNumber} ({totalPrice}):\n{appliances}\n{trackingUrl}",
                                "whatsappReadyTemplate", "Pronto OS #{orderNumber} ({totalPrice}):\n{appliances}\n{trackingUrl}"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.whatsappIntakeTemplate").value("Entrada OS #{orderNumber} para {customerName}:\n{appliances}\n{trackingUrl}"))
                .andExpect(jsonPath("$.whatsappReadyTemplate").value("Pronto OS #{orderNumber} ({totalPrice}):\n{appliances}\n{trackingUrl}"));

        UUID customerId = createCustomer(shop.token(), "Pedro Souza", "11933332222");
        JsonNode order = createOrder(shop.token(), customerId, null, List.of(
                Map.of("applianceType", "Cafeteira Expresso", "brand", "Oster", "voltage", "V110", "reportedIssue", "Not heating"),
                Map.of("applianceType", "Air Fryer", "brand", "Mondial", "voltage", "V110", "reportedIssue", "Timer broken")
        ));
        UUID orderId = UUID.fromString(order.get("id").asString());
        UUID item1Id = UUID.fromString(order.get("items").get(0).get("id").asString());
        UUID item2Id = UUID.fromString(order.get("items").get(1).get("id").asString());

        // INTAKE consolidated message includes BOTH appliances
        MvcResult intakeRes = mockMvc.perform(get("/api/v1/orders/{id}/whatsapp-link", orderId)
                        .param("stage", "INTAKE")
                        .header("Authorization", "Bearer " + shop.token()))
                .andExpect(status().isOk())
                .andReturn();
        String intakeMsg = objectMapper.readTree(intakeRes.getResponse().getContentAsString()).get("message").asString();
        assertThat(intakeMsg)
                .contains("Entrada OS #1001 para Pedro Souza:")
                .contains("#1001-1 Cafeteira Expresso Oster")
                .contains("#1001-2 Air Fryer Mondial");

        // Update quotes and check QUOTE consolidated message with summed total
        transition(shop.token(), orderId, item1Id, "DIAGNOSING", null).andExpect(status().isOk());
        updateQuote(shop.token(), orderId, item1Id, "Pump", "80.00", "70.00", "0.00", 90).andExpect(status().isOk());
        transition(shop.token(), orderId, item1Id, "QUOTE_SENT", null).andExpect(status().isOk());

        transition(shop.token(), orderId, item2Id, "DIAGNOSING", null).andExpect(status().isOk());
        updateQuote(shop.token(), orderId, item2Id, "Timer", "80.00", "20.00", "0.00", 90).andExpect(status().isOk());
        transition(shop.token(), orderId, item2Id, "QUOTE_SENT", null).andExpect(status().isOk());

        MvcResult quoteRes = mockMvc.perform(get("/api/v1/orders/{id}/whatsapp-link", orderId)
                        .param("stage", "QUOTE")
                        .header("Authorization", "Bearer " + shop.token()))
                .andExpect(status().isOk())
                .andReturn();
        String quoteMsg = objectMapper.readTree(quoteRes.getResponse().getContentAsString()).get("message").asString();
        assertThat(quoteMsg)
                .contains("Orcamento OS #1001 (R$ 250,00):")
                .contains("#1001-1 Cafeteira Expresso Oster — R$ 150,00")
                .contains("#1001-2 Air Fryer Mondial — R$ 100,00");

        // Move item 1 to READY_FOR_PICKUP and check READY consolidated message
        transition(shop.token(), orderId, item1Id, "APPROVED", null).andExpect(status().isOk());
        transition(shop.token(), orderId, item1Id, "IN_REPAIR", null).andExpect(status().isOk());
        transition(shop.token(), orderId, item1Id, "READY_FOR_PICKUP", null).andExpect(status().isOk());

        MvcResult readyRes = mockMvc.perform(get("/api/v1/orders/{id}/whatsapp-link", orderId)
                        .param("stage", "READY")
                        .header("Authorization", "Bearer " + shop.token()))
                .andExpect(status().isOk())
                .andReturn();
        String readyMsg = objectMapper.readTree(readyRes.getResponse().getContentAsString()).get("message").asString();
        assertThat(readyMsg)
                .contains("Pronto OS #1001 (R$ 250,00):")
                .contains("#1001-1 Cafeteira Expresso Oster — Pronto p/ retirada (R$ 150,00)")
                .contains("#1001-2 Air Fryer Mondial — Em andamento");

        // Remove custom appliance type
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/tenant/settings/appliance-types")
                        .param("name", "Cafeteira Expresso")
                        .header("Authorization", "Bearer " + shop.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customApplianceTypes").isEmpty());
    }

    @Test
    void opensLinkedWarrantyReturnOrderWithoutFinancialDistortionAndEnforcesTenantIsolation() throws Exception {
        RegisteredShop shopA = registerShop("Oficina Garantia A", "77888999000111", "11911112222", "Dono A", "garantia-a@shop.com", "StrongPass123!");
        RegisteredShop shopB = registerShop("Oficina Garantia B", "88999000000122", "11922223333", "Dono B", "garantia-b@shop.com", "StrongPass123!");

        UUID customerId = createCustomer(shopA.token(), "Fernanda Costa", "11988885555");
        JsonNode originalOrder = createOrder(shopA.token(), customerId, null, List.of(
                Map.of(
                        "applianceType", "Micro-ondas",
                        "brand", "Electrolux",
                        "model", "MEF41",
                        "voltage", "V110",
                        "serialNumber", "SN-998877",
                        "accessories", "Prato de vidro",
                        "reportedIssue", "Não aquece"
                )
        ));
        UUID originalOrderId = UUID.fromString(originalOrder.get("id").asString());
        UUID originalItemId = UUID.fromString(originalOrder.get("items").get(0).get("id").asString());

        // Cannot open warranty return before item is DELIVERED -> 422
        mockMvc.perform(post("/api/v1/orders/{orderId}/items/{itemId}/warranty-return", originalOrderId, originalItemId)
                        .header("Authorization", "Bearer " + shopA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "reportedIssue", "Parou de aquecer novamente"
                        ))))
                .andExpect(status().is(422));

        // Complete and deliver original repair with 90-day warranty and R$ 220,00 payment
        transition(shopA.token(), originalOrderId, originalItemId, "DIAGNOSING", null).andExpect(status().isOk());
        updateQuote(shopA.token(), originalOrderId, originalItemId, "Troca de magnetron", "100.00", "120.00", "0.00", 90).andExpect(status().isOk());
        transition(shopA.token(), originalOrderId, originalItemId, "QUOTE_SENT", null).andExpect(status().isOk());
        transition(shopA.token(), originalOrderId, originalItemId, "APPROVED", null).andExpect(status().isOk());
        transition(shopA.token(), originalOrderId, originalItemId, "IN_REPAIR", null).andExpect(status().isOk());
        transition(shopA.token(), originalOrderId, originalItemId, "READY_FOR_PICKUP", null).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/orders/{orderId}/items/{itemId}/deliver", originalOrderId, originalItemId)
                        .header("Authorization", "Bearer " + shopA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "payments", List.of(Map.of("paymentMethod", "PIX", "amount", new BigDecimal("220.00")))
                        ))))
                .andExpect(status().isOk());

        // Cross-tenant warranty return attempt returns 404
        mockMvc.perform(post("/api/v1/orders/{orderId}/items/{itemId}/warranty-return", originalOrderId, originalItemId)
                        .header("Authorization", "Bearer " + shopB.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "reportedIssue", "Tentativa indevida"
                        ))))
                .andExpect(status().isNotFound());

        // Open warranty return for the delivered item -> 201 Created linked to original item
        MvcResult returnRes = mockMvc.perform(post("/api/v1/orders/{orderId}/items/{itemId}/warranty-return", originalOrderId, originalItemId)
                        .header("Authorization", "Bearer " + shopA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "reportedIssue", "Voltou a não aquecer após 10 dias",
                                "notes", "Prioridade garantia"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderNumber").value(1002))
                .andExpect(jsonPath("$.customerId").value(customerId.toString()))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].warrantyReturn").value(true))
                .andExpect(jsonPath("$.items[0].parentItemId").value(originalItemId.toString()))
                .andExpect(jsonPath("$.items[0].parentOrderId").value(originalOrderId.toString()))
                .andExpect(jsonPath("$.items[0].parentItemTag").value("1001-1"))
                .andExpect(jsonPath("$.items[0].applianceType").value("Micro-ondas"))
                .andExpect(jsonPath("$.items[0].brand").value("Electrolux"))
                .andExpect(jsonPath("$.items[0].model").value("MEF41"))
                .andExpect(jsonPath("$.items[0].serialNumber").value("SN-998877"))
                .andExpect(jsonPath("$.items[0].accessories").value("Prato de vidro"))
                .andExpect(jsonPath("$.items[0].totalPrice").value(0.00))
                .andExpect(jsonPath("$.items[0].warrantyDays").value(90))
                .andReturn();

        JsonNode returnOrder = objectMapper.readTree(returnRes.getResponse().getContentAsString());
        UUID returnOrderId = UUID.fromString(returnOrder.get("id").asString());
        UUID returnItemId = UUID.fromString(returnOrder.get("items").get(0).get("id").asString());

        // Bench queue exposes warrantyReturn badge & parentItemId
        mockMvc.perform(get("/api/v1/order-items")
                        .param("tag", "1002-1")
                        .header("Authorization", "Bearer " + shopA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].warrantyReturn").value(true))
                .andExpect(jsonPath("$.content[0].parentItemId").value(originalItemId.toString()));

        // Customer order history returns both orders with parentItemTag resolved
        mockMvc.perform(get("/api/v1/orders")
                        .param("customerId", customerId.toString())
                        .header("Authorization", "Bearer " + shopA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].items[0].warrantyReturn").value(true))
                .andExpect(jsonPath("$.content[0].items[0].parentItemTag").value("1001-1"));

        // Covered warranty fast-track: RECEIVED -> DIAGNOSING -> APPROVED (R$ 0,00) -> IN_REPAIR -> READY_FOR_PICKUP -> DELIVERED (no revenue impact)
        transition(shopA.token(), returnOrderId, returnItemId, "DIAGNOSING", null).andExpect(status().isOk());
        transition(shopA.token(), returnOrderId, returnItemId, "APPROVED", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warrantyReturn").value(true))
                .andExpect(jsonPath("$.parentItemTag").value("1001-1"));
        transition(shopA.token(), returnOrderId, returnItemId, "IN_REPAIR", null).andExpect(status().isOk());
        transition(shopA.token(), returnOrderId, returnItemId, "READY_FOR_PICKUP", null).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/orders/{orderId}/items/{itemId}/deliver", returnOrderId, returnItemId)
                        .header("Authorization", "Bearer " + shopA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.totalPrice").value(0.00));

        // Only the original paid repair generated an OrderItemDeliveredEvent (zero financial distortion for warranty return)
        List<OrderItemDeliveredEvent> deliveredEvents = applicationEvents.stream(OrderItemDeliveredEvent.class).toList();
        assertThat(deliveredEvents).hasSize(1);
        assertThat(deliveredEvents.getFirst().itemId()).isEqualTo(originalItemId);
    }

    private String createUserAndLogin(String ownerToken, String name, String email, String password, String role) throws Exception {
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

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", password
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(loginResult.getResponse().getContentAsString()).get("accessToken").asString();
    }

    private org.springframework.test.web.servlet.ResultActions updateQuote(
            String token, UUID orderId, UUID itemId,
            String diagnosis, String labor, String parts, String discount, int warrantyDays
    ) throws Exception {
        return mockMvc.perform(patch("/api/v1/orders/{orderId}/items/{itemId}/quote", orderId, itemId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "technicalDiagnosis", diagnosis,
                        "laborCost", new BigDecimal(labor),
                        "partsCost", new BigDecimal(parts),
                        "discount", new BigDecimal(discount),
                        "warrantyDays", warrantyDays
                ))));
    }

    private org.springframework.test.web.servlet.ResultActions transition(
            String token, UUID orderId, UUID itemId, String targetStatus, String cancellationReason
    ) throws Exception {
        Map<String, Object> body = cancellationReason != null
                ? Map.of("status", targetStatus, "cancellationReason", cancellationReason)
                : Map.of("status", targetStatus);
        return mockMvc.perform(post("/api/v1/orders/{orderId}/items/{itemId}/status", orderId, itemId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
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

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return UUID.fromString(json.get("id").asString());
    }

    private JsonNode createOrder(
            String token, UUID customerId, String notes, List<Map<String, Object>> items
    ) throws Exception {
        Map<String, Object> body = notes != null
                ? Map.of("customerId", customerId, "notes", notes, "items", items)
                : Map.of("customerId", customerId, "items", items);

        MvcResult result = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private record RegisteredShop(UUID tenantId, String token) {
    }
}
