package com.benchos.api.order;

import com.benchos.api.TestcontainersConfiguration;
import com.benchos.api.order.internal.entity.ServiceOrder;
import com.benchos.api.order.internal.repository.ServiceOrderRepository;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ServiceOrderIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ServiceOrderRepository serviceOrderRepository;

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
        jdbcTemplate.execute("DELETE FROM service_order_items");
        jdbcTemplate.execute("DELETE FROM service_orders");
        jdbcTemplate.execute("DELETE FROM tenant_sequences");
        jdbcTemplate.execute("DELETE FROM customers");
        jdbcTemplate.execute("DELETE FROM users");
        jdbcTemplate.execute("DELETE FROM tenant_settings");
        jdbcTemplate.execute("DELETE FROM tenants");
    }

    @Test
    void createsMultiItemOrdersWithIsolatedTenantNumberingBenchQueueAndCrossTenantIsolation() throws Exception {
        RegisteredShop shopA = registerShop("Oficina A", "12345678000101", "11999990001", "Carlos", "carlos@shopa.com", "Secret123!");
        RegisteredShop shopB = registerShop("Oficina B", "98765432000102", "11999990002", "Marcos", "marcos@shopb.com", "Secret123!");

        UUID mariaA = createCustomer(shopA.token(), "Maria Silva", "(11) 98888-1111");
        UUID joaoA = createCustomer(shopA.token(), "Joao Souza", "(11) 97777-2222");
        UUID deletedCustomerA = createCustomer(shopA.token(), "Cliente Removido", "(11) 96666-3333");
        UUID anaB = createCustomer(shopB.token(), "Ana Lima", "(11) 95555-4444");

        // Soft-delete one customer in Shop A
        mockMvc.perform(delete("/api/v1/customers/{id}", deletedCustomerA)
                        .header("Authorization", "Bearer " + shopA.token()))
                .andExpect(status().isNoContent());

        // Cannot create order for soft-deleted customer or cross-tenant customer -> 404
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + shopA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "customerId", deletedCustomerA,
                                "items", List.of(microwaveItem())
                        ))))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + shopA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "customerId", anaB,
                                "items", List.of(microwaveItem())
                        ))))
                .andExpect(status().isNotFound());

        // Cannot create order with empty items list -> 400
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + shopA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "customerId", mariaA,
                                "items", List.of()
                        ))))
                .andExpect(status().isBadRequest());

        // Shop A Order #1001 with 2 appliances -> tags 1001-1 and 1001-2
        JsonNode order1001A = createOrder(shopA.token(), mariaA, "Urgent for Saturday", List.of(
                microwaveItem(),
                airFryerItem()
        ));
        UUID order1001AId = UUID.fromString(order1001A.get("id").asString());
        UUID publicToken1001A = UUID.fromString(order1001A.get("publicToken").asString());

        assertThat(order1001A.get("orderNumber").asLong()).isEqualTo(1001L);
        assertThat(publicToken1001A.version()).isEqualTo(4);
        assertThat(order1001A.get("customerName").asString()).isEqualTo("Maria Silva");
        assertThat(order1001A.get("items").size()).isEqualTo(2);
        assertThat(order1001A.get("items").get(0).get("tag").asString()).isEqualTo("1001-1");
        assertThat(order1001A.get("items").get(0).get("status").asString()).isEqualTo("RECEIVED");
        assertThat(order1001A.get("items").get(0).get("warrantyDays").asInt()).isEqualTo(90);
        assertThat(order1001A.get("items").get(1).get("tag").asString()).isEqualTo("1001-2");
        assertThat(order1001A.get("items").get(1).get("status").asString()).isEqualTo("RECEIVED");

        // Shop A Order #1002 with 1 appliance -> tag 1002-1
        JsonNode order1002A = createOrder(shopA.token(), joaoA, null, List.of(blenderItem()));
        assertThat(order1002A.get("orderNumber").asLong()).isEqualTo(1002L);
        assertThat(order1002A.get("items").size()).isEqualTo(1);
        assertThat(order1002A.get("items").get(0).get("tag").asString()).isEqualTo("1002-1");

        // Shop B starts independently at Order #1001 -> tag 1001-1
        JsonNode order1001B = createOrder(shopB.token(), anaB, "Shop B order", List.of(microwaveItem()));
        UUID order1001BId = UUID.fromString(order1001B.get("id").asString());
        assertThat(order1001B.get("orderNumber").asLong()).isEqualTo(1001L);
        assertThat(order1001B.get("items").get(0).get("tag").asString()).isEqualTo("1001-1");

        // GET /api/v1/orders/{id} works within tenant and returns 404 cross-tenant
        mockMvc.perform(get("/api/v1/orders/{id}", order1001AId)
                        .header("Authorization", "Bearer " + shopA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNumber").value(1001))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].tag").value("1001-1"))
                .andExpect(jsonPath("$.items[1].tag").value("1001-2"));

        mockMvc.perform(get("/api/v1/orders/{id}", order1001BId)
                        .header("Authorization", "Bearer " + shopA.token()))
                .andExpect(status().isNotFound());

        // Paginated GET /api/v1/orders with filters
        mockMvc.perform(get("/api/v1/orders")
                        .header("Authorization", "Bearer " + shopA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content.length()").value(2));

        mockMvc.perform(get("/api/v1/orders")
                        .param("orderNumber", "1001")
                        .header("Authorization", "Bearer " + shopA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(order1001AId.toString()));

        mockMvc.perform(get("/api/v1/orders")
                        .param("customerId", joaoA.toString())
                        .header("Authorization", "Bearer " + shopA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].orderNumber").value(1002));

        mockMvc.perform(get("/api/v1/orders")
                        .param("status", "IN_REPAIR")
                        .header("Authorization", "Bearer " + shopA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        // Bench queue GET /api/v1/order-items isolates by tenant and supports status + tag filters
        mockMvc.perform(get("/api/v1/order-items")
                        .param("status", "RECEIVED")
                        .header("Authorization", "Bearer " + shopA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].tag").value("1001-1"))
                .andExpect(jsonPath("$.content[1].tag").value("1001-2"))
                .andExpect(jsonPath("$.content[2].tag").value("1002-1"));

        mockMvc.perform(get("/api/v1/order-items")
                        .param("tag", "1001-2")
                        .header("Authorization", "Bearer " + shopA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].tag").value("1001-2"))
                .andExpect(jsonPath("$.content[0].applianceType").value("Air Fryer"))
                .andExpect(jsonPath("$.content[0].customerName").value("Maria Silva"));

        mockMvc.perform(get("/api/v1/order-items")
                        .param("tag", "1001")
                        .header("Authorization", "Bearer " + shopA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get("/api/v1/order-items")
                        .param("tag", "invalid-tag")
                        .header("Authorization", "Bearer " + shopA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        // Shop B bench queue only sees Shop B's 1 item (not Shop A's 1001-1 or 1001-2)
        mockMvc.perform(get("/api/v1/order-items")
                        .param("tag", "1001")
                        .header("Authorization", "Bearer " + shopB.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].customerName").value("Ana Lima"));

        // Hibernate @Filter isolates plain findAll() on service_orders
        TenantContext.setCurrentTenant(shopA.tenantId());
        try {
            List<ServiceOrder> filteredOrders = transactionTemplate.execute(status -> serviceOrderRepository.findAll());
            assertThat(filteredOrders)
                    .hasSize(2)
                    .allSatisfy(order -> assertThat(order.getTenantId()).isEqualTo(shopA.tenantId()));
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void assignsUniqueSequentialOrderNumbersUnderConcurrentOrderCreation() throws Exception {
        RegisteredShop shop = registerShop("Oficina Concorrente", "55566677000188", "11944443333", "Pedro", "pedro@conc.com", "Secret123!");
        UUID customerId = createCustomer(shop.token(), "Cliente Teste", "(11) 93333-2222");

        int concurrentRequests = 10;
        CountDownLatch readyLatch = new CountDownLatch(concurrentRequests);
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);

        List<Future<Long>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < concurrentRequests; i++) {
                futures.add(executor.submit(() -> {
                    readyLatch.countDown();
                    assertThat(startLatch.await(10, TimeUnit.SECONDS)).isTrue();
                    JsonNode order = createOrder(shop.token(), customerId, null, List.of(blenderItem()));
                    return order.get("orderNumber").asLong();
                }));
            }

            assertThat(readyLatch.await(10, TimeUnit.SECONDS)).isTrue();
            startLatch.countDown();

            Set<Long> assignedNumbers = new HashSet<>();
            for (Future<Long> future : futures) {
                assignedNumbers.add(future.get(30, TimeUnit.SECONDS));
            }

            assertThat(assignedNumbers)
                    .hasSize(concurrentRequests)
                    .containsExactlyInAnyOrder(1001L, 1002L, 1003L, 1004L, 1005L, 1006L, 1007L, 1008L, 1009L, 1010L);
        } finally {
            executor.shutdownNow();
        }
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
            String token,
            UUID customerId,
            String notes,
            List<Map<String, Object>> items
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

    private static Map<String, Object> microwaveItem() {
        return Map.of(
                "applianceType", "Microwave",
                "brand", "Electrolux",
                "model", "MEF41",
                "voltage", "V110",
                "serialNumber", "SN-MW-01",
                "accessories", "Glass turntable",
                "reportedIssue", "Not heating food"
        );
    }

    private static Map<String, Object> airFryerItem() {
        return Map.of(
                "applianceType", "Air Fryer",
                "brand", "Mondial",
                "model", "AF-30",
                "voltage", "V220",
                "reportedIssue", "Does not turn on"
        );
    }

    private static Map<String, Object> blenderItem() {
        return Map.of(
                "applianceType", "Blender",
                "brand", "Philco",
                "voltage", "BIVOLT",
                "reportedIssue", "Burnt smell from motor"
        );
    }

    private record RegisteredShop(UUID tenantId, String token) {
    }
}
