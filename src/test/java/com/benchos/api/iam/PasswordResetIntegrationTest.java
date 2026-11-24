package com.benchos.api.iam;

import com.benchos.api.TestcontainersConfiguration;
import com.benchos.api.iam.internal.repository.PasswordResetTokenRepository;
import com.benchos.api.iam.internal.repository.TenantRepository;
import com.benchos.api.iam.internal.repository.TenantSettingsRepository;
import com.benchos.api.iam.internal.repository.UserRepository;
import com.benchos.api.shared.mail.EmailMessage;
import com.benchos.api.shared.mail.EmailSender;
import com.benchos.api.shared.tenant.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "benchos.mail.resend-cooldown=0s")
class PasswordResetIntegrationTest {

    private static final Pattern SIX_DIGIT_CODE = Pattern.compile("\\b(\\d{6})\\b");

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantSettingsRepository tenantSettingsRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @MockitoSpyBean
    private EmailSender emailSender;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        TenantContext.clear();
        tokenRepository.deleteAll();
        userRepository.deleteAll();
        tenantSettingsRepository.deleteAll();
        tenantRepository.deleteAll();
        clearInvocations(emailSender);
    }

    @Test
    void resetsPasswordWithEmailCodeAndBlocksReuse() throws Exception {
        registerShop("Oficina Reset", "12345678000101", "11999990001", "Carlos", "carlos@reset.com", "OldPass123!");

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "CARLOS@RESET.COM"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").isNotEmpty());

        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailSender, times(1)).send(captor.capture());

        EmailMessage sent = captor.getValue();
        assertThat(sent.to()).isEqualTo("carlos@reset.com");
        String code = extractCode(sent.textBody());

        // Reset password with captured 6-digit code
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "carlos@reset.com",
                                "code", code,
                                "newPassword", "NewStrongPass9!"
                        ))))
                .andExpect(status().isOk());

        // Old password no longer works
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "carlos@reset.com",
                                "password", "OldPass123!"
                        ))))
                .andExpect(status().isUnauthorized());

        // New password works
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "carlos@reset.com",
                                "password", "NewStrongPass9!"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        // Reusing the same code fails with 422
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "carlos@reset.com",
                                "code", code,
                                "newPassword", "AnotherPass9!"
                        ))))
                .andExpect(status().is(422));
    }

    @Test
    void enforcesAntiEnumerationAndBruteForceLockout() throws Exception {
        RegisteredShop shop = registerShop("Oficina Segura", "22345678000102", "11999990002", "Ana", "ana@segura.com", "InitPass123!");

        // Unknown email returns 202 without sending email
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "nobody@nowhere.com"))))
                .andExpect(status().isAccepted());
        verify(emailSender, never()).send(any());

        // Inactive user returns 202 without sending email
        UUID techId = createUser(shop.token(), "Bruno", "bruno@segura.com", "TechPass123!", "TECHNICIAN");
        mockMvc.perform(patch("/api/v1/users/{id}/status", techId)
                        .header("Authorization", "Bearer " + shop.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("active", false))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "bruno@segura.com"))))
                .andExpect(status().isAccepted());
        verify(emailSender, never()).send(any());

        // Request valid code for Ana, then fail 5 times -> token locks out even for valid code
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "ana@segura.com"))))
                .andExpect(status().isAccepted());

        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailSender, times(1)).send(captor.capture());
        String validCode = extractCode(captor.getValue().textBody());
        String wrongCode = validCode.equals("000000") ? "111111" : "000000";

        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post("/api/v1/auth/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "email", "ana@segura.com",
                                    "code", wrongCode,
                                    "newPassword", "HackedPass123!"
                            ))))
                    .andExpect(status().is(422));
        }

        // Now even the original valid code must be rejected
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "ana@segura.com",
                                "code", validCode,
                                "newPassword", "ValidPassAfterLock1!"
                        ))))
                .andExpect(status().is(422));
    }

    @Test
    void ownerResetsTeamMemberPasswordWithRbacAndTenantIsolation() throws Exception {
        RegisteredShop shopA = registerShop("Oficina A", "32345678000103", "11999990003", "Dono A", "donoa@shop.com", "OwnerPass1!");
        RegisteredShop shopB = registerShop("Oficina B", "42345678000104", "11999990004", "Dono B", "donob@shop.com", "OwnerPass1!");

        UUID techAId = createUser(shopA.token(), "Tecnico A", "teca@shop.com", "OldTechPass1!", "TECHNICIAN");
        UUID techBId = createUser(shopB.token(), "Tecnico B", "tecb@shop.com", "TechBPass1!", "TECHNICIAN");

        // Technician cannot reset passwords -> 403
        String techAToken = login("teca@shop.com", "OldTechPass1!");
        mockMvc.perform(patch("/api/v1/users/{id}/password", techAId)
                        .header("Authorization", "Bearer " + techAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("newPassword", "SelfHack123!"))))
                .andExpect(status().isForbidden());

        // Cross-tenant owner gets 404 and cannot modify Shop B's technician
        mockMvc.perform(patch("/api/v1/users/{id}/password", techBId)
                        .header("Authorization", "Bearer " + shopA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("newPassword", "CrossTenant1!"))))
                .andExpect(status().isNotFound());

        // Shop B technician still logs in with original password
        login("tecb@shop.com", "TechBPass1!");

        // Owner A resets Technician A's password -> Technician A logs in with new password
        mockMvc.perform(patch("/api/v1/users/{id}/password", techAId)
                        .header("Authorization", "Bearer " + shopA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("newPassword", "ResetByOwner9!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("teca@shop.com"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "teca@shop.com",
                                "password", "OldTechPass1!"
                        ))))
                .andExpect(status().isUnauthorized());

        login("teca@shop.com", "ResetByOwner9!");
    }

    private static String extractCode(String textBody) {
        Matcher matcher = SIX_DIGIT_CODE.matcher(textBody);
        assertThat(matcher.find()).as("Expected 6-digit code in email body").isTrue();
        return matcher.group(1);
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
