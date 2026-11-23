package com.benchos.api.customer;

import com.benchos.api.TestcontainersConfiguration;
import com.benchos.api.customer.internal.entity.Customer;
import com.benchos.api.customer.internal.repository.CustomerRepository;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CustomerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private CustomerQuery customerQuery;

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
        TenantContext.clear();
        jdbcTemplate.execute("DELETE FROM customers");
        jdbcTemplate.execute("DELETE FROM users");
        jdbcTemplate.execute("DELETE FROM tenant_settings");
        jdbcTemplate.execute("DELETE FROM tenants");
    }

    @Test
    void supportsFastCounterSearchPhoneUniquenessPerTenantAndCrossTenantIsolation() throws Exception {
        RegisteredShop shopA = registerShop("Oficina A", "12345678000101", "11999990001", "Carlos", "carlos@shopa.com", "Secret123!");
        RegisteredShop shopB = registerShop("Oficina B", "98765432000102", "11999990002", "Marcos", "marcos@shopb.com", "Secret123!");

        UUID mariaId = createCustomer(shopA.token(), "Maria Oliveira", "(11) 98888-1111", "12345678901", "Prefers WhatsApp");
        UUID joaoId = createCustomer(shopA.token(), "Joao Santos", "(11) 97777-2222", null, null);

        // Same phone number in Shop B is allowed without conflict
        UUID shopBMariaId = createCustomer(shopB.token(), "Maria Outro Bairro", "(11) 98888-1111", null, "Shop B customer");

        // Duplicate phone in same shop (even differently formatted) returns 409 Conflict
        mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", "Bearer " + shopA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Maria Duplicada",
                                "phone", "11988881111"
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        // Invalid phone returns 422 Business Rule Violation
        mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", "Bearer " + shopA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Telefone Invalido",
                                "phone", "(11) ---"
                        ))))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.status").value(422));

        // List all active customers in Shop A
        mockMvc.perform(get("/api/v1/customers")
                        .header("Authorization", "Bearer " + shopA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Joao Santos"))
                .andExpect(jsonPath("$[1].name").value("Maria Oliveira"))
                .andExpect(jsonPath("$[1].phone").value("11988881111"));

        // Fast counter search by formatted phone fragment
        mockMvc.perform(get("/api/v1/customers")
                        .param("query", "98888-1111")
                        .header("Authorization", "Bearer " + shopA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(mariaId.toString()));

        // Fast counter search by case-insensitive name fragment
        mockMvc.perform(get("/api/v1/customers")
                        .param("query", "santos")
                        .header("Authorization", "Bearer " + shopA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(joaoId.toString()));

        // Update customer keeping own phone works; colliding with another active customer returns 409
        mockMvc.perform(put("/api/v1/customers/{id}", mariaId)
                        .header("Authorization", "Bearer " + shopA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Maria Oliveira Souza",
                                "phone", "(11) 98888-1111",
                                "notes", "Updated note"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Maria Oliveira Souza"))
                .andExpect(jsonPath("$.phone").value("11988881111"))
                .andExpect(jsonPath("$.notes").value("Updated note"));

        mockMvc.perform(put("/api/v1/customers/{id}", mariaId)
                        .header("Authorization", "Bearer " + shopA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Maria Oliveira Souza",
                                "phone", "11 97777-2222"
                        ))))
                .andExpect(status().isConflict());

        // Cross-tenant GET / PUT / DELETE return 404 (no existence leakage)
        mockMvc.perform(get("/api/v1/customers/{id}", shopBMariaId)
                        .header("Authorization", "Bearer " + shopA.token()))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/v1/customers/{id}", shopBMariaId)
                        .header("Authorization", "Bearer " + shopA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Hacked",
                                "phone", "11911112222"
                        ))))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/customers/{id}", shopBMariaId)
                        .header("Authorization", "Bearer " + shopA.token()))
                .andExpect(status().isNotFound());

        // Public module contract CustomerQuery respects tenant boundary
        Optional<CustomerSnapshot> ownSnapshot = customerQuery.findActiveById(mariaId, shopA.tenantId());
        assertThat(ownSnapshot).isPresent();
        assertThat(ownSnapshot.get().name()).isEqualTo("Maria Oliveira Souza");
        assertThat(ownSnapshot.get().phone()).isEqualTo("11988881111");

        assertThat(customerQuery.findActiveById(shopBMariaId, shopA.tenantId())).isEmpty();

        // Hibernate @Filter isolates plain findAll() inside @Transactional
        TenantContext.setCurrentTenant(shopA.tenantId());
        try {
            List<Customer> filtered = transactionTemplate.execute(status -> customerRepository.findAll());
            assertThat(filtered)
                    .hasSize(2)
                    .allSatisfy(c -> assertThat(c.getTenantId()).isEqualTo(shopA.tenantId()));
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void softDeleteHidesCustomerAllowsPhoneReuseAndEnforcesRbac() throws Exception {
        RegisteredShop shop = registerShop("Oficina Central", "11122233000199", "11988887777", "Paulo", "paulo@central.com", "MasterPass1!");

        createUser(shop.token(), "Ana Atendente", "ana@central.com", "AttPass123!", "ATTENDANT");
        createUser(shop.token(), "Lucas Tecnico", "lucas@central.com", "TechPass123!", "TECHNICIAN");
        String attendantToken = login("ana@central.com", "AttPass123!");
        String techToken = login("lucas@central.com", "TechPass123!");

        // Technician can create, read and update customers
        UUID customerId = createCustomer(techToken, "Roberto Lima", "(11) 96666-3333", null, "Airfryer customer");

        mockMvc.perform(get("/api/v1/customers/{id}", customerId)
                        .header("Authorization", "Bearer " + techToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("11966663333"));

        mockMvc.perform(put("/api/v1/customers/{id}", customerId)
                        .header("Authorization", "Bearer " + techToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Roberto Lima Jr",
                                "phone", "11966663333"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Roberto Lima Jr"));

        // Technician cannot soft delete customers -> 403 Forbidden
        mockMvc.perform(delete("/api/v1/customers/{id}", customerId)
                        .header("Authorization", "Bearer " + techToken))
                .andExpect(status().isForbidden());

        // Attendant can soft delete customers -> 204 No Content
        mockMvc.perform(delete("/api/v1/customers/{id}", customerId)
                        .header("Authorization", "Bearer " + attendantToken))
                .andExpect(status().isNoContent());

        // Soft-deleted customer is hidden from GET, search, and CustomerQuery (row still preserved in DB)
        mockMvc.perform(get("/api/v1/customers/{id}", customerId)
                        .header("Authorization", "Bearer " + shop.token()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/customers")
                        .param("query", "966663333")
                        .header("Authorization", "Bearer " + shop.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        assertThat(customerQuery.findActiveById(customerId, shop.tenantId())).isEmpty();

        Integer deletedRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM customers WHERE id = ? AND deleted_at IS NOT NULL",
                Integer.class,
                customerId
        );
        assertThat(deletedRows).isEqualTo(1);

        // Phone number can be re-registered after soft delete (partial unique index)
        UUID reRegisteredId = createCustomer(attendantToken, "Roberto Reativado", "(11) 96666-3333", null, null);
        assertThat(reRegisteredId).isNotEqualTo(customerId);

        mockMvc.perform(get("/api/v1/customers")
                        .param("query", "96666")
                        .header("Authorization", "Bearer " + shop.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(reRegisteredId.toString()))
                .andExpect(jsonPath("$[0].name").value("Roberto Reativado"));
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

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("accessToken").asString();
    }

    private UUID createCustomer(String token, String name, String phone, String document, String notes) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("phone", phone);
        if (document != null) {
            body.put("document", document);
        }
        if (notes != null) {
            body.put("notes", notes);
        }

        MvcResult result = mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return UUID.fromString(json.get("id").asString());
    }

    private record RegisteredShop(UUID tenantId, String token) {
    }
}
