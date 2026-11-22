package com.benchos.api.iam;

import com.benchos.api.TestcontainersConfiguration;
import com.benchos.api.iam.internal.entity.User;
import com.benchos.api.iam.internal.repository.TenantRepository;
import com.benchos.api.iam.internal.repository.TenantSettingsRepository;
import com.benchos.api.iam.internal.repository.UserRepository;
import com.benchos.api.shared.tenant.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class IamTenantIsolationIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantSettingsRepository tenantSettingsRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private TenantSettingsQuery tenantSettingsQuery;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        TenantContext.clear();
        userRepository.deleteAll();
        tenantSettingsRepository.deleteAll();
        tenantRepository.deleteAll();
    }

    @Test
    void enforcesStrictTenantIsolationBetweenShopAAndShopB() throws Exception {
        RegisteredShop shopA = registerShop("Oficina do Pai", "12345678000101", "11999990001", "Carlos", "carlos@shopa.com", "Secret123!");
        RegisteredShop shopB = registerShop("Oficina do Primo", "98765432000102", "11999990002", "Marcos", "marcos@shopb.com", "Secret123!");

        UUID techAId = createUser(shopA.token(), "Joao Tecnico", "joao@shopa.com", "TechPass123!", "TECHNICIAN");
        UUID attendantBId = createUser(shopB.token(), "Ana Balcao", "ana@shopb.com", "AttPass123!", "ATTENDANT");

        // Shop A sees only Shop A's 2 users
        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + shopA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].email").value("carlos@shopa.com"))
                .andExpect(jsonPath("$[1].email").value("joao@shopa.com"));

        // Shop B sees only Shop B's 2 users
        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + shopB.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].email").value("marcos@shopb.com"))
                .andExpect(jsonPath("$[1].email").value("ana@shopb.com"));

        // Cross-tenant GET by ID returns 404 (not 403, avoiding existence leakage)
        mockMvc.perform(get("/api/v1/users/{id}", attendantBId)
                        .header("Authorization", "Bearer " + shopA.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        // Cross-tenant PATCH returns 404 and leaves Shop B's user active
        mockMvc.perform(patch("/api/v1/users/{id}/status", attendantBId)
                        .header("Authorization", "Bearer " + shopA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("active", false))))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/users/{id}", attendantBId)
                        .header("Authorization", "Bearer " + shopB.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        // Updating Shop A settings does not affect Shop B settings
        mockMvc.perform(put("/api/v1/tenant/settings")
                        .header("Authorization", "Bearer " + shopA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "defaultLaborCost", new BigDecimal("85.50"),
                                "defaultEstimatedDays", 5,
                                "abandonmentDays", 180,
                                "whatsappQuoteTemplate", "Custom Shop A template"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultLaborCost").value(85.50))
                .andExpect(jsonPath("$.defaultEstimatedDays").value(5));

        TenantSettingsSnapshot shopASnapshot = tenantSettingsQuery.getSettings(shopA.tenantId());
        TenantSettingsSnapshot shopBSnapshot = tenantSettingsQuery.getSettings(shopB.tenantId());
        assertThat(shopASnapshot.defaultLaborCost()).isEqualByComparingTo("85.50");
        assertThat(shopBSnapshot.defaultLaborCost()).isEqualByComparingTo("0.00");
        assertThat(shopBSnapshot.defaultEstimatedDays()).isEqualTo(3);
        assertThat(shopBSnapshot.abandonmentDays()).isEqualTo(180);

        // Hibernate @Filter isolates plain findAll() queries automatically inside @Transactional
        TenantContext.setCurrentTenant(shopA.tenantId());
        try {
            List<User> filteredUsers = transactionTemplate.execute(status -> userRepository.findAll());
            assertThat(filteredUsers)
                    .hasSize(2)
                    .allSatisfy(user -> assertThat(user.getTenantId()).isEqualTo(shopA.tenantId()));
            assertThat(filteredUsers)
                    .extracting(User::getId)
                    .contains(shopA.ownerId(), techAId)
                    .doesNotContain(shopB.ownerId(), attendantBId);
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void enforcesAuthRbacAndAccountLifecycleRules() throws Exception {
        RegisteredShop shopA = registerShop("Oficina Central", "11122233000199", "11988887777", "Paulo", "paulo@central.com", "MasterPass1!");

        // Duplicate global email returns 409 Conflict
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "shopName", "Outra Oficina",
                                "document", "99988877000111",
                                "phone", "11977776666",
                                "ownerName", "Outro",
                                "email", "PAULO@CENTRAL.COM",
                                "password", "AnotherPass1!"
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        // Login with wrong password returns 401
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "paulo@central.com",
                                "password", "WrongPassword!"
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));

        // Create technician and login as technician
        UUID techId = createUser(shopA.token(), "Lucas", "lucas@central.com", "TechPass123!", "TECHNICIAN");
        String techToken = login("lucas@central.com", "TechPass123!");

        // Technician can read own profile and tenant settings
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + techToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("lucas@central.com"))
                .andExpect(jsonPath("$.role").value("TECHNICIAN"));

        mockMvc.perform(get("/api/v1/tenant/settings")
                        .header("Authorization", "Bearer " + techToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.abandonmentDays").value(180));

        // Technician cannot manage users or update settings -> 403 Forbidden
        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + techToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/tenant/settings")
                        .header("Authorization", "Bearer " + techToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "defaultLaborCost", new BigDecimal("50.00"),
                                "defaultEstimatedDays", 2,
                                "abandonmentDays", 90,
                                "whatsappQuoteTemplate", "Blocked"
                        ))))
                .andExpect(status().isForbidden());

        // Owner cannot deactivate own account -> 422
        mockMvc.perform(patch("/api/v1/users/{id}/status", shopA.ownerId())
                        .header("Authorization", "Bearer " + shopA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("active", false))))
                .andExpect(status().is(422));

        // Owner deactivates technician -> technician can no longer log in
        mockMvc.perform(patch("/api/v1/users/{id}/status", techId)
                        .header("Authorization", "Bearer " + shopA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("active", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "lucas@central.com",
                                "password", "TechPass123!"
                        ))))
                .andExpect(status().isUnauthorized());
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
                UUID.fromString(json.get("user").get("id").asString()),
                json.get("accessToken").asString()
        );
    }

    private UUID createUser(String ownerToken, String name, String email, String password, String role) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", name,
                                "email", email,
                                "password", password,
                                "role", role
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return UUID.fromString(json.get("id").asString());
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

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("accessToken").asString();
    }

    private record RegisteredShop(UUID tenantId, UUID ownerId, String token) {
    }
}
