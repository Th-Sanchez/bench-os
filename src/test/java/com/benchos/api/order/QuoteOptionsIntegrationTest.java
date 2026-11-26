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

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class QuoteOptionsIntegrationTest {

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
    void supportsOptionalMultiOptionQuotesPublicCustomerSelectionAndSinglePriceFallback() throws Exception {
        String token = registerShop("Oficina Opcoes", "11222333000181", "11999990001", "Dono", "dono@opcoes.com");
        UUID customerId = createCustomer(token, "Juliana Lima", "11988883333");
        JsonNode order = createOrder(token, customerId);
        UUID orderId = UUID.fromString(order.get("id").asString());
        UUID itemId = UUID.fromString(order.get("items").get(0).get("id").asString());
        String publicToken = order.get("publicToken").asString();

        transition(token, orderId, itemId, "DIAGNOSING");

        // 1. Default single-price quote still works cleanly with 0 options
        mockMvc.perform(patch("/api/v1/orders/{orderId}/items/{itemId}/quote", orderId, itemId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "technicalDiagnosis", "Troca de magnetron",
                                "laborCost", new BigDecimal("100.00"),
                                "partsCost", new BigDecimal("120.00"),
                                "partsCostActual", new BigDecimal("60.00"),
                                "discount", BigDecimal.ZERO,
                                "warrantyDays", 90
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPrice").value(220.00))
                .andExpect(jsonPath("$.quoteOptions.length()").value(0));

        // 2. Save multi-option quote (Peça Original 180d vs Peça Paralela 90d)
        MvcResult multiRes = mockMvc.perform(patch("/api/v1/orders/{orderId}/items/{itemId}/quote", orderId, itemId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "technicalDiagnosis", "Troca de magnetron com opções de peça",
                                "laborCost", new BigDecimal("100.00"),
                                "partsCost", new BigDecimal("160.00"),
                                "discount", BigDecimal.ZERO,
                                "warrantyDays", 180,
                                "quoteOptions", List.of(
                                        Map.of(
                                                "label", "Peça Original",
                                                "description", "Magnetron original Panasonic com garantia estendida",
                                                "laborCost", new BigDecimal("100.00"),
                                                "partsCost", new BigDecimal("160.00"),
                                                "partsCostActual", new BigDecimal("95.00"),
                                                "discount", BigDecimal.ZERO,
                                                "warrantyDays", 180,
                                                "recommended", true
                                        ),
                                        Map.of(
                                                "label", "Peça Paralela",
                                                "description", "Opção econômica compatível",
                                                "laborCost", new BigDecimal("100.00"),
                                                "partsCost", new BigDecimal("90.00"),
                                                "partsCostActual", new BigDecimal("45.00"),
                                                "discount", new BigDecimal("10.00"),
                                                "warrantyDays", 90,
                                                "recommended", false
                                        )
                                )
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPrice").value(260.00))
                .andExpect(jsonPath("$.warrantyDays").value(180))
                .andExpect(jsonPath("$.quoteOptions.length()").value(2))
                .andExpect(jsonPath("$.quoteOptions[0].label").value("Peça Original"))
                .andExpect(jsonPath("$.quoteOptions[1].totalPrice").value(180.00))
                .andReturn();

        JsonNode multiJson = objectMapper.readTree(multiRes.getResponse().getContentAsString());
        UUID parallelOptionId = UUID.fromString(multiJson.get("quoteOptions").get(1).get("id").asString());

        transition(token, orderId, itemId, "QUOTE_SENT");

        // 3. Public /track API exposes both options WITHOUT internal partsCostActual
        mockMvc.perform(get("/api/v1/public/orders/{publicToken}", publicToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quoteOptions.length()").value(2))
                .andExpect(jsonPath("$.items[0].quoteOptions[0].label").value("Peça Original"))
                .andExpect(jsonPath("$.items[0].quoteOptions[0].partsCostActual").doesNotExist())
                .andExpect(jsonPath("$.items[0].quoteOptions[1].label").value("Peça Paralela"))
                .andExpect(jsonPath("$.items[0].quoteOptions[1].totalPrice").value(180.00))
                .andExpect(jsonPath("$.items[0].quoteOptions[1].warrantyDays").value(90));

        // 4. Reject unknown quoteOptionId on public approval
        mockMvc.perform(post("/api/v1/public/orders/{publicToken}/items/1/approve", publicToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("quoteOptionId", UUID.randomUUID()))))
                .andExpect(status().isUnprocessableContent());

        // 5. Customer chooses "Peça Paralela" on /track -> item updates totalPrice, warranty, actual cost & label!
        mockMvc.perform(post("/api/v1/public/orders/{publicToken}/items/1/approve", publicToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("quoteOptionId", parallelOptionId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("APPROVED"))
                .andExpect(jsonPath("$.items[0].totalPrice").value(180.00))
                .andExpect(jsonPath("$.items[0].warrantyDays").value(90))
                .andExpect(jsonPath("$.items[0].selectedQuoteOptionLabel").value("Peça Paralela"));

        // 6. Verify internal order detail reflects chosen option prices & internal actual cost (45.00 -> net profit 135.00)
        mockMvc.perform(get("/api/v1/orders/{orderId}", orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].selectedQuoteOptionId").value(parallelOptionId.toString()))
                .andExpect(jsonPath("$.items[0].selectedQuoteOptionLabel").value("Peça Paralela"))
                .andExpect(jsonPath("$.items[0].totalPrice").value(180.00))
                .andExpect(jsonPath("$.items[0].partsCostActual").value(45.00))
                .andExpect(jsonPath("$.items[0].estimatedNetProfit").value(135.00));

        // 7. Reopen to QUOTE_SENT clears selected option and allows switching back to single-price quote
        transition(token, orderId, itemId, "QUOTE_SENT");
        mockMvc.perform(patch("/api/v1/orders/{orderId}/items/{itemId}/quote", orderId, itemId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "technicalDiagnosis", "Preço único fechado",
                                "laborCost", new BigDecimal("100.00"),
                                "partsCost", new BigDecimal("100.00"),
                                "partsCostActual", new BigDecimal("50.00"),
                                "discount", BigDecimal.ZERO,
                                "warrantyDays", 90,
                                "quoteOptions", List.of()
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPrice").value(200.00))
                .andExpect(jsonPath("$.quoteOptions.length()").value(0))
                .andExpect(jsonPath("$.selectedQuoteOptionLabel").doesNotExist());
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
                                        "brand", "Panasonic",
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
