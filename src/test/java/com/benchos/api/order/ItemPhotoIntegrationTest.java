package com.benchos.api.order;

import com.benchos.api.TestcontainersConfiguration;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ItemPhotoIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private S3Client s3Client;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        reset(s3Client);
        cleanDatabase();
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    private void cleanDatabase() {
        TenantContext.clear();
        jdbcTemplate.execute("DELETE FROM item_photos");
        jdbcTemplate.execute("DELETE FROM service_order_items");
        jdbcTemplate.execute("DELETE FROM service_orders");
        jdbcTemplate.execute("DELETE FROM tenant_sequences");
        jdbcTemplate.execute("DELETE FROM customers");
        jdbcTemplate.execute("DELETE FROM users");
        jdbcTemplate.execute("DELETE FROM tenant_settings");
        jdbcTemplate.execute("DELETE FROM tenants");
    }

    @Test
    void presignsRegistersListsExposesOnPublicViewDeletesAndIsolatesItemPhotosAcrossTenants() throws Exception {
        RegisteredShop shopA = registerShop("Oficina Foto A", "12345678000101", "11999990001", "Carlos", "carlos@fotoa.com", "Secret123!");
        RegisteredShop shopB = registerShop("Oficina Foto B", "98765432000102", "11999990002", "Marcos", "marcos@fotob.com", "Secret123!");

        UUID customerA = createCustomer(shopA.token(), "Maria Silva", "(11) 98888-1111");
        JsonNode orderA = createOrder(shopA.token(), customerA);
        UUID orderAId = UUID.fromString(orderA.get("id").asString());
        UUID publicTokenA = UUID.fromString(orderA.get("publicToken").asString());
        UUID itemAId = UUID.fromString(orderA.get("items").get(0).get("id").asString());

        // 1. Reject unsupported MIME type -> 422
        mockMvc.perform(post("/api/v1/orders/{orderId}/items/{itemId}/photos/presign", orderAId, itemAId)
                        .header("Authorization", "Bearer " + shopA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "stage", "INTAKE",
                                "contentType", "application/pdf"
                        ))))
                .andExpect(status().is(422));

        // 2. Presign INTAKE JPEG upload -> returns real SigV4 URL and tenant/order/item-scoped key
        MvcResult presignIntakeResult = mockMvc.perform(post("/api/v1/orders/{orderId}/items/{itemId}/photos/presign", orderAId, itemAId)
                        .header("Authorization", "Bearer " + shopA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "stage", "INTAKE",
                                "contentType", "image/jpeg"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("INTAKE"))
                .andExpect(jsonPath("$.contentType").value("image/jpeg"))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andReturn();

        JsonNode presignIntake = objectMapper.readTree(presignIntakeResult.getResponse().getContentAsString());
        String intakeKey = presignIntake.get("storageKey").asString();
        String uploadUrl = presignIntake.get("uploadUrl").asString();
        assertThat(intakeKey)
                .startsWith("tenants/" + shopA.tenantId() + "/orders/" + orderAId + "/items/" + itemAId + "/")
                .endsWith(".jpg");
        assertThat(uploadUrl)
                .contains("X-Amz-Algorithm=AWS4-HMAC-SHA256")
                .contains("X-Amz-Signature=");

        // 3. Register uploaded photo -> 201 Created with signed viewUrl
        MvcResult registeredResult = mockMvc.perform(post("/api/v1/orders/{orderId}/items/{itemId}/photos", orderAId, itemAId)
                        .header("Authorization", "Bearer " + shopA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "storageKey", intakeKey,
                                "stage", "INTAKE",
                                "contentType", "image/jpeg"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.storageKey").value(intakeKey))
                .andExpect(jsonPath("$.stage").value("INTAKE"))
                .andExpect(jsonPath("$.viewUrl").isNotEmpty())
                .andReturn();

        JsonNode registeredPhoto = objectMapper.readTree(registeredResult.getResponse().getContentAsString());
        UUID photoId = UUID.fromString(registeredPhoto.get("id").asString());
        assertThat(registeredPhoto.get("viewUrl").asString())
                .contains("X-Amz-Algorithm=AWS4-HMAC-SHA256")
                .contains("X-Amz-Signature=");

        // 4. Re-registering same storageKey -> 409 Conflict
        mockMvc.perform(post("/api/v1/orders/{orderId}/items/{itemId}/photos", orderAId, itemAId)
                        .header("Authorization", "Bearer " + shopA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "storageKey", intakeKey,
                                "stage", "INTAKE",
                                "contentType", "image/jpeg"
                        ))))
                .andExpect(status().isConflict());

        // 5. Registering tampered storageKey (wrong tenant prefix) -> 422
        mockMvc.perform(post("/api/v1/orders/{orderId}/items/{itemId}/photos", orderAId, itemAId)
                        .header("Authorization", "Bearer " + shopA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "storageKey", "tenants/" + shopB.tenantId() + "/orders/" + orderAId + "/items/" + itemAId + "/" + UUID.randomUUID() + ".jpg",
                                "stage", "DIAGNOSIS",
                                "contentType", "image/jpeg"
                        ))))
                .andExpect(status().is(422));

        // 6. List photos returns presigned view URLs
        mockMvc.perform(get("/api/v1/orders/{orderId}/items/{itemId}/photos", orderAId, itemAId)
                        .header("Authorization", "Bearer " + shopA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(photoId.toString()))
                .andExpect(jsonPath("$[0].stage").value("INTAKE"));

        // 7. Public tracking endpoint exposes signed viewUrl without leaking internal storageKey
        MvcResult publicResult = mockMvc.perform(get("/api/v1/public/orders/{publicToken}", publicTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].photos.length()").value(1))
                .andExpect(jsonPath("$.items[0].photos[0].stage").value("INTAKE"))
                .andExpect(jsonPath("$.items[0].photos[0].viewUrl").isNotEmpty())
                .andReturn();

        String publicBody = publicResult.getResponse().getContentAsString();
        assertThat(publicBody).doesNotContain("storageKey");

        // 8. Cross-tenant access to Shop A's item photos -> 404
        mockMvc.perform(post("/api/v1/orders/{orderId}/items/{itemId}/photos/presign", orderAId, itemAId)
                        .header("Authorization", "Bearer " + shopB.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "stage", "INTAKE",
                                "contentType", "image/jpeg"
                        ))))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/orders/{orderId}/items/{itemId}/photos", orderAId, itemAId)
                        .header("Authorization", "Bearer " + shopB.token()))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/orders/{orderId}/items/{itemId}/photos/{photoId}", orderAId, itemAId, photoId)
                        .header("Authorization", "Bearer " + shopB.token()))
                .andExpect(status().isNotFound());

        // 9. Delete photo removes DB record and calls S3 deleteObject with exact bucket & key
        mockMvc.perform(delete("/api/v1/orders/{orderId}/items/{itemId}/photos/{photoId}", orderAId, itemAId, photoId)
                        .header("Authorization", "Bearer " + shopA.token()))
                .andExpect(status().isNoContent());

        ArgumentCaptor<DeleteObjectRequest> deleteCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(deleteCaptor.capture());
        assertThat(deleteCaptor.getValue().bucket()).isEqualTo("benchos-media");
        assertThat(deleteCaptor.getValue().key()).isEqualTo(intakeKey);

        mockMvc.perform(get("/api/v1/orders/{orderId}/items/{itemId}/photos", orderAId, itemAId)
                        .header("Authorization", "Bearer " + shopA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
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

    private JsonNode createOrder(String token, UUID customerId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "customerId", customerId,
                                "items", List.of(Map.of(
                                        "applianceType", "Microwave",
                                        "brand", "Electrolux",
                                        "model", "MEF41",
                                        "voltage", "V110",
                                        "reportedIssue", "Not heating"
                                ))
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private record RegisteredShop(UUID tenantId, String token) {
    }
}
