package com.benchos.api.shared.ratelimit;

import com.benchos.api.TestcontainersConfiguration;
import com.benchos.api.iam.internal.repository.PasswordResetTokenRepository;
import com.benchos.api.iam.internal.repository.TenantRepository;
import com.benchos.api.iam.internal.repository.TenantSettingsRepository;
import com.benchos.api.iam.internal.repository.UserRepository;
import com.benchos.api.shared.tenant.TenantContext;
import com.benchos.api.shared.web.RequestCorrelationFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
        "benchos.security.rate-limit.enabled=true",
        "benchos.security.rate-limit.login.ip-max-requests=4",
        "benchos.security.rate-limit.login.ip-window=5m",
        "benchos.security.rate-limit.login.account-max-requests=2",
        "benchos.security.rate-limit.login.account-window=5m",
        "benchos.security.rate-limit.register.ip-max-requests=2",
        "benchos.security.rate-limit.register.ip-window=15m",
        "benchos.security.rate-limit.forgot-password.ip-max-requests=4",
        "benchos.security.rate-limit.forgot-password.account-max-requests=2",
        "benchos.security.rate-limit.reset-password.ip-max-requests=4",
        "benchos.security.rate-limit.reset-password.account-max-requests=2",
        "benchos.security.rate-limit.public-read.ip-max-requests=3",
        "benchos.security.rate-limit.public-read.ip-window=1m",
        "benchos.security.rate-limit.public-write.ip-max-requests=2",
        "benchos.security.rate-limit.public-write.ip-window=5m"
})
class RateLimitIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private RequestCorrelationFilter requestCorrelationFilter;

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

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(requestCorrelationFilter)
                .apply(springSecurity())
                .build();
        TenantContext.clear();
        tokenRepository.deleteAll();
        userRepository.deleteAll();
        tenantSettingsRepository.deleteAll();
        tenantRepository.deleteAll();
    }

    @Test
    void blocksDistributedLoginBruteForcePerAccountAcrossDifferentIpsAndTrailingSlashes() throws Exception {
        registerShop("Oficina Shield", "12345678000199", "11988881111", "Ana", "ana@shield.com", "StrongPass1!", "198.51.100.1");

        // Attempt 1 from IP A (mixed case) -> reaches AuthController -> 401
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(remoteIp("203.0.113.10"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "ANA@SHIELD.COM",
                                "password", "Wrong1!"
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(RateLimitFilter.HEADER_LIMIT, "2"))
                .andExpect(header().string(RateLimitFilter.HEADER_REMAINING, "1"));

        // Attempt 2 from IP B with different casing -> shares exact same normalized account bucket -> 401
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(remoteIp("203.0.113.11"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "Ana@Shield.Com",
                                "password", "Wrong2!"
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(RateLimitFilter.HEADER_REMAINING, "0"));

        // Attempt 3 from IP C -> blocked by account brute-force limit with RFC 7807 429
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(remoteIp("203.0.113.12"))
                        .header(RequestCorrelationFilter.REQUEST_ID_HEADER, "rl-check-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "ana@shield.com",
                                "password", "StrongPass1!"
                        ))))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(header().string(RateLimitFilter.HEADER_LIMIT, "2"))
                .andExpect(header().string(RateLimitFilter.HEADER_REMAINING, "0"))
                .andExpect(header().string(RequestCorrelationFilter.REQUEST_ID_HEADER, "rl-check-001"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.title").value("Too Many Requests"))
                .andExpect(jsonPath("$.type").value("https://benchos.com/errors/rate-limit-exceeded"))
                .andExpect(jsonPath("$.requestId").value("rl-check-001"))
                .andExpect(jsonPath("$.retryAfterSeconds").isNumber());

        // Trailing slash and whitespace padding cannot bypass the normalized rate-limit bucket
        mockMvc.perform(post("/api/v1/auth/login/")
                        .with(remoteIp("203.0.113.13"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "  ana@shield.com ",
                                "password", "StrongPass1!"
                        ))))
                .andExpect(status().isTooManyRequests());

        // Another user from another IP can still authenticate cleanly
        registerShop("Oficina Livre", "98765432000188", "11977772222", "Beto", "beto@livre.com", "BetoPass1!", "198.51.100.2");
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(remoteIp("203.0.113.99"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "beto@livre.com",
                                "password", "BetoPass1!"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void blocksPasswordSprayingFromSingleIpAcrossDifferentAccounts() throws Exception {
        String attackerIp = "203.0.113.50";
        for (int i = 1; i <= 4; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .with(remoteIp(attackerIp))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "email", "user" + i + "@spray.com",
                                    "password", "Password123!"
                            ))))
                    .andExpect(status().isUnauthorized());
        }

        // 5th attempt from same IP hits IP limit (4) even with a brand new email
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(remoteIp(attackerIp))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "user5@spray.com",
                                "password", "Password123!"
                        ))))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.status").value(429));
    }

    @Test
    void limitsRegistrationForgotPasswordAndResetPasswordEndpoints() throws Exception {
        String clientIp = "198.51.100.20";

        // Register allows 2 per IP, blocks 3rd
        registerShop("Shop 1", "11111111000101", "11911110001", "Owner 1", "o1@shop.com", "Pass1234!", clientIp);
        registerShop("Shop 2", "22222222000102", "11911110002", "Owner 2", "o2@shop.com", "Pass1234!", clientIp);

        mockMvc.perform(post("/api/v1/auth/register")
                        .with(remoteIp(clientIp))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "shopName", "Shop 3",
                                "document", "33333333000103",
                                "phone", "11911110003",
                                "ownerName", "Owner 3",
                                "email", "o3@shop.com",
                                "password", "Pass1234!"
                        ))))
                .andExpect(status().isTooManyRequests());

        // Forgot password allows 2 per account, blocks 3rd
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/auth/forgot-password")
                            .with(remoteIp("198.51.100." + (30 + i)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("email", "o1@shop.com"))))
                    .andExpect(status().isAccepted());
        }
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .with(remoteIp("198.51.100.39"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "o1@shop.com"))))
                .andExpect(status().isTooManyRequests());

        // Reset password allows 2 per account, blocks 3rd
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/auth/reset-password")
                            .with(remoteIp("198.51.100." + (40 + i)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "email", "o1@shop.com",
                                    "code", "123456",
                                    "newPassword", "NewPass123!"
                            ))))
                    .andExpect(status().is(422));
        }
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .with(remoteIp("198.51.100.49"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "o1@shop.com",
                                "code", "123456",
                                "newPassword", "NewPass123!"
                        ))))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void enforcesSeparateLimitsForPublicReadAndWriteWhileSkippingOptionsAndHealth() throws Exception {
        String ip = "198.51.100.77";
        UUID randomToken = UUID.randomUUID();

        // OPTIONS preflight must never consume quota
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(options("/api/v1/public/orders/" + randomToken)
                            .with(remoteIp(ip)))
                    .andExpect(header().doesNotExist(RateLimitFilter.HEADER_LIMIT));
        }

        // Public reads (/track and /api/v1/public/orders) share public-read limit = 3
        mockMvc.perform(get("/track/" + randomToken).with(remoteIp(ip)))
                .andExpect(status().isOk())
                .andExpect(header().string(RateLimitFilter.HEADER_REMAINING, "2"));
        mockMvc.perform(get("/track/" + randomToken + "/receipt").with(remoteIp(ip)))
                .andExpect(status().isOk())
                .andExpect(header().string(RateLimitFilter.HEADER_REMAINING, "1"));
        mockMvc.perform(get("/api/v1/public/orders/" + randomToken).with(remoteIp(ip)))
                .andExpect(status().isNotFound())
                .andExpect(header().string(RateLimitFilter.HEADER_REMAINING, "0"));
        mockMvc.perform(get("/track/" + randomToken).with(remoteIp(ip)))
                .andExpect(status().isTooManyRequests());

        // Public writes (/approve and /reject) have their own bucket (limit = 2)
        mockMvc.perform(post("/api/v1/public/orders/" + randomToken + "/items/1/approve").with(remoteIp(ip)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/public/orders/" + randomToken + "/items/1/reject").with(remoteIp(ip)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/public/orders/" + randomToken + "/items/1/approve").with(remoteIp(ip)))
                .andExpect(status().isTooManyRequests());

        // Health endpoint is exempt from public rate limit
        mockMvc.perform(get("/actuator/health/readiness").with(remoteIp(ip)))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(RateLimitFilter.HEADER_LIMIT));
    }

    private void registerShop(
            String shopName,
            String document,
            String phone,
            String ownerName,
            String email,
            String password,
            String remoteIp
    ) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .with(remoteIp(remoteIp))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "shopName", shopName,
                                "document", document,
                                "phone", phone,
                                "ownerName", ownerName,
                                "email", email,
                                "password", password
                        ))))
                .andExpect(status().isCreated());
    }

    private static RequestPostProcessor remoteIp(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }
}
