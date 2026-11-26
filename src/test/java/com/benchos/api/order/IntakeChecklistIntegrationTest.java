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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class IntakeChecklistIntegrationTest {

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
    void managesCategoryChecklistsAndPersistsSnapshotOnOrderAndPublicTrack() throws Exception {
        String token = registerShop();

        // New shop starts with curated default intake checklists (e.g. Micro-ondas)
        mockMvc.perform(get("/api/v1/tenant/settings")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intakeChecklistTemplates['Micro-ondas']", hasItem("Prato giratório devolvido ao cliente")));

        // Owner customizes checklists per category
        mockMvc.perform(put("/api/v1/tenant/settings/intake-checklists")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "templates", Map.of(
                                        "Micro-ondas", List.of("Prato retirado", "Aro retirado", "Liga na tomada"),
                                        "Bebedouro", List.of("Pingadeira entregue", "Liga na tomada")
                                )
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intakeChecklistTemplates['Micro-ondas']", hasSize(3)))
                .andExpect(jsonPath("$.intakeChecklistTemplates['Bebedouro']", hasSize(2)));

        // Create order with intake checklist snapshot
        UUID customerId = createCustomer(token);
        MvcResult createResult = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "customerId", customerId.toString(),
                                "items", List.of(Map.of(
                                        "applianceType", "Micro-ondas",
                                        "brand", "Electrolux",
                                        "model", "MEF41",
                                        "voltage", "V110",
                                        "reportedIssue", "Nao esquenta",
                                        "intakeChecklist", List.of(
                                                Map.of("label", "Prato retirado", "checked", true),
                                                Map.of("label", "Aro retirado", "checked", true),
                                                Map.of("label", "Liga na tomada", "checked", false)
                                        )
                                ))
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].intakeChecklist", hasSize(3)))
                .andExpect(jsonPath("$.items[0].intakeChecklist[0].label").value("Prato retirado"))
                .andExpect(jsonPath("$.items[0].intakeChecklist[0].checked").value(true))
                .andExpect(jsonPath("$.items[0].intakeChecklist[2].checked").value(false))
                .andReturn();

        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
        UUID publicToken = UUID.fromString(created.get("publicToken").asString());

        // Public tracking endpoint also exposes the intake checklist snapshot
        mockMvc.perform(get("/api/v1/public/orders/{publicToken}", publicToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].intakeChecklist", hasSize(3)))
                .andExpect(jsonPath("$.items[0].intakeChecklist[0].label").value("Prato retirado"))
                .andExpect(jsonPath("$.items[0].intakeChecklist[0].checked").value(true));

        // Reset checklists restores built-in defaults
        mockMvc.perform(delete("/api/v1/tenant/settings/intake-checklists")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intakeChecklistTemplates['Micro-ondas']", hasItem("Prato giratório devolvido ao cliente")));
    }

    private String registerShop() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "shopName", "Oficina Checklist",
                                "document", "55667788000199",
                                "phone", "11999990000",
                                "ownerName", "Roberto",
                                "email", "roberto@checklist.com",
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
                                "name", "Joao Silva",
                                "phone", "11988886666"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asString());
    }
}
