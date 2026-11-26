package com.benchos.api.iam;

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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ShopBrandingIntegrationTest {

    // Minimal valid 1x1 transparent PNG
    private static final String VALID_PNG_DATA_URL =
            "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=";

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
    void validatesLogoAndExposesShopBrandingOnPublicTracking() throws Exception {
        String token = registerShop();

        // Reject SVG payload (XSS protection)
        mockMvc.perform(put("/api/v1/tenant/settings/branding")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "shopLogoDataUrl", "data:image/svg+xml;base64,PHN2Zz48c2NyaXB0Pjwvc2NyaXB0Pjwvc3ZnPg=="
                        ))))
                .andExpect(status().is(422));

        // Reject spoofed PNG header (magic bytes check)
        mockMvc.perform(put("/api/v1/tenant/settings/branding")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "shopLogoDataUrl", "data:image/png;base64,aGVsbG8gd29ybGQ="
                        ))))
                .andExpect(status().is(422));

        // Save valid PNG logo, address, business hours, and custom warranty terms
        mockMvc.perform(put("/api/v1/tenant/settings/branding")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "shopLogoDataUrl", VALID_PNG_DATA_URL,
                                "shopAddress", "Rua das Flores, 123 - Centro, Sao Paulo/SP",
                                "businessHours", "Seg a Sex 8h30-18h | Sab 8h30-13h",
                                "warrantyTerms", "Garantia de 90 dias exclusiva para o servico realizado."
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shopLogoDataUrl").value(startsWith("data:image/png;base64,")))
                .andExpect(jsonPath("$.shopAddress").value("Rua das Flores, 123 - Centro, Sao Paulo/SP"))
                .andExpect(jsonPath("$.businessHours").value("Seg a Sex 8h30-18h | Sab 8h30-13h"))
                .andExpect(jsonPath("$.warrantyTerms").value("Garantia de 90 dias exclusiva para o servico realizado."));

        // Public tracking endpoint exposes branding fields
        UUID customerId = createCustomer(token);
        MvcResult orderRes = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "customerId", customerId.toString(),
                                "items", List.of(Map.of(
                                        "applianceType", "Micro-ondas",
                                        "brand", "Consul",
                                        "voltage", "V110",
                                        "reportedIssue", "Nao liga"
                                ))
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode order = objectMapper.readTree(orderRes.getResponse().getContentAsString());
        UUID publicToken = UUID.fromString(order.get("publicToken").asString());

        mockMvc.perform(get("/api/v1/public/orders/{publicToken}", publicToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shopLogoDataUrl").value(startsWith("data:image/png;base64,")))
                .andExpect(jsonPath("$.shopAddress").value("Rua das Flores, 123 - Centro, Sao Paulo/SP"))
                .andExpect(jsonPath("$.businessHours").value("Seg a Sex 8h30-18h | Sab 8h30-13h"))
                .andExpect(jsonPath("$.warrantyTerms").value("Garantia de 90 dias exclusiva para o servico realizado."));

        // Clearing logo works without touching address/hours
        mockMvc.perform(put("/api/v1/tenant/settings/branding")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("clearLogo", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shopLogoDataUrl").value(nullValue()))
                .andExpect(jsonPath("$.shopAddress").value("Rua das Flores, 123 - Centro, Sao Paulo/SP"));
    }

    private String registerShop() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "shopName", "Oficina Marca",
                                "document", "77889900000155",
                                "phone", "11977776666",
                                "ownerName", "Ana",
                                "email", "ana@oficinamarca.com",
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
                                "name", "Pedro Lima",
                                "phone", "11966665555"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asString());
    }
}
