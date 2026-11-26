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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DeliverySignatureAndReviewIntegrationTest {

    private static final String VALID_SIGNATURE_SVG =
            "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 520 170\" fill=\"none\">"
                    + "<path d=\"M12 80L64 42L120 90\" stroke=\"#0F172A\" stroke-width=\"2.5\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/>"
                    + "</svg>";

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
    void capturesDigitalSignatureOnDeliveryAndHandlesPostDeliveryStarRatingWithGoogleReviewUrl() throws Exception {
        String token = registerShop();

        // Digital pickup signature is disabled by default for new shops
        mockMvc.perform(get("/api/v1/tenant/settings")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliverySignatureEnabled").value(false));

        // Reject invalid non-http(s) Google review URL
        mockMvc.perform(put("/api/v1/tenant/settings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "defaultLaborCost", new BigDecimal("70.00"),
                                "defaultEstimatedDays", 3,
                                "abandonmentDays", 180,
                                "whatsappQuoteTemplate", "Template {trackingUrl}",
                                "googleReviewUrl", "javascript:alert(1)"
                        ))))
                .andExpect(status().is(422));

        // Configure valid Google Business review URL and enable digital pickup signature
        String googleUrl = "https://g.page/r/OficinaTeste123/review";
        mockMvc.perform(put("/api/v1/tenant/settings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "defaultLaborCost", new BigDecimal("70.00"),
                                "defaultEstimatedDays", 3,
                                "abandonmentDays", 180,
                                "whatsappQuoteTemplate", "Template {trackingUrl}",
                                "googleReviewUrl", googleUrl,
                                "deliverySignatureEnabled", true
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.googleReviewUrl").value(googleUrl))
                .andExpect(jsonPath("$.deliverySignatureEnabled").value(true));

        UUID customerId = createCustomer(token);
        JsonNode order = createOrder(token, customerId);
        UUID orderId = UUID.fromString(order.get("id").asString());
        UUID publicToken = UUID.fromString(order.get("publicToken").asString());
        UUID itemId = UUID.fromString(order.get("items").get(0).get("id").asString());

        // Rating before delivery is rejected (422)
        mockMvc.perform(post("/api/v1/public/orders/{publicToken}/rating", publicToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("rating", 5))))
                .andExpect(status().is(422));

        // Advance item to READY_FOR_PICKUP
        transition(token, orderId, itemId, "DIAGNOSING");
        mockMvc.perform(patch("/api/v1/orders/{orderId}/items/{itemId}/quote", orderId, itemId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "technicalDiagnosis", "Replaced fuse",
                                "laborCost", new BigDecimal("80.00"),
                                "partsCost", new BigDecimal("20.00"),
                                "discount", BigDecimal.ZERO,
                                "warrantyDays", 90
                        ))))
                .andExpect(status().isOk());
        transition(token, orderId, itemId, "QUOTE_SENT");
        transition(token, orderId, itemId, "APPROVED");
        transition(token, orderId, itemId, "IN_REPAIR");
        transition(token, orderId, itemId, "READY_FOR_PICKUP");

        // Unsafe XSS SVG signature is rejected (422)
        mockMvc.perform(post("/api/v1/orders/{orderId}/items/{itemId}/deliver", orderId, itemId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "payments", List.of(Map.of("paymentMethod", "PIX", "amount", new BigDecimal("100.00"))),
                                "deliverySignatureSvg", "<svg viewBox=\"0 0 100 50\"><script>alert(1)</script></svg>"
                        ))))
                .andExpect(status().is(422));

        // Valid digital signature + signer name is persisted on delivery
        mockMvc.perform(post("/api/v1/orders/{orderId}/items/{itemId}/deliver", orderId, itemId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "payments", List.of(Map.of("paymentMethod", "PIX", "amount", new BigDecimal("100.00"))),
                                "deliverySignatureSvg", VALID_SIGNATURE_SVG,
                                "deliverySignerName", "Joao Filho"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.deliverySignatureSvg").value(containsString("M12 80L64 42L120 90")))
                .andExpect(jsonPath("$.deliverySignerName").value("Joao Filho"));

        // Public tracking response exposes signature SVG and Google review URL without leaking signer PII
        mockMvc.perform(get("/api/v1/public/orders/{publicToken}", publicToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.googleReviewUrl").value(googleUrl))
                .andExpect(jsonPath("$.customerRating").value(nullValue()))
                .andExpect(jsonPath("$.items[0].deliverySignatureSvg").value(containsString("M12 80L64 42L120 90")))
                .andExpect(jsonPath("$.items[0].deliverySignerName").doesNotExist());

        // Out-of-range rating fails validation (400)
        mockMvc.perform(post("/api/v1/public/orders/{publicToken}/rating", publicToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("rating", 6))))
                .andExpect(status().isBadRequest());

        // Submit valid 5-star customer rating + feedback
        mockMvc.perform(post("/api/v1/public/orders/{publicToken}/rating", publicToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "rating", 5,
                                "feedback", "Atendimento super rapido e transparente!"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerRating").value(5))
                .andExpect(jsonPath("$.customerFeedback").value("Atendimento super rapido e transparente!"))
                .andExpect(jsonPath("$.customerRatedAt").isNotEmpty())
                .andExpect(jsonPath("$.googleReviewUrl").value(googleUrl));

        // Duplicate rating submission is rejected (422)
        mockMvc.perform(post("/api/v1/public/orders/{publicToken}/rating", publicToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("rating", 4))))
                .andExpect(status().is(422));

        // Authenticated workshop view shows both signature and customer rating
        mockMvc.perform(get("/api/v1/orders/{orderId}", orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerRating").value(5))
                .andExpect(jsonPath("$.customerFeedback").value("Atendimento super rapido e transparente!"))
                .andExpect(jsonPath("$.customerRatedAt").isNotEmpty())
                .andExpect(jsonPath("$.items[0].deliverySignatureSvg").value(containsString("M12 80L64 42L120 90")))
                .andExpect(jsonPath("$.items[0].deliverySignerName").value("Joao Filho"));

        // Clearing googleReviewUrl with empty string resets it to null while preserving deliverySignatureEnabled
        mockMvc.perform(put("/api/v1/tenant/settings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "defaultLaborCost", new BigDecimal("70.00"),
                                "defaultEstimatedDays", 3,
                                "abandonmentDays", 180,
                                "whatsappQuoteTemplate", "Template {trackingUrl}",
                                "googleReviewUrl", ""
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.googleReviewUrl").value(nullValue()))
                .andExpect(jsonPath("$.deliverySignatureEnabled").value(true));

        // Owner can disable digital pickup signature again
        mockMvc.perform(put("/api/v1/tenant/settings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "defaultLaborCost", new BigDecimal("70.00"),
                                "defaultEstimatedDays", 3,
                                "abandonmentDays", 180,
                                "whatsappQuoteTemplate", "Template {trackingUrl}",
                                "deliverySignatureEnabled", false
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliverySignatureEnabled").value(false));
    }

    private String registerShop() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "shopName", "Oficina Digital",
                                "document", "12345678000199",
                                "phone", "11999998888",
                                "ownerName", "Carlos",
                                "email", "carlos@oficinadigital.com",
                                "password", "Secret123!"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asString();
    }

    private UUID createCustomer(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Maria Oliveira",
                                "phone", "11988887777"
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
                                "customerId", customerId.toString(),
                                "items", List.of(Map.of(
                                        "applianceType", "Micro-ondas",
                                        "brand", "Brastemp",
                                        "model", "BM20",
                                        "voltage", "V110",
                                        "reportedIssue", "Nao liga"
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
