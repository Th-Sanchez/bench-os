package com.benchos.api.shared.security;

import com.benchos.api.TestcontainersConfiguration;
import com.benchos.api.shared.tenant.TenantAwareEntity;
import com.benchos.api.shared.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import org.hibernate.Filter;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Import({TestcontainersConfiguration.class, SecurityIntegrationTest.ProbeConfig.class})
class SecurityIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JwtTokenService jwtTokenService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void allowsPublicEndpointWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/public/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void rejectsProtectedEndpointWithoutTokenUsingProblemDetail() throws Exception {
        mockMvc.perform(get("/api/v1/test/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }

    @Test
    void authenticatesJwtBindsTenantContextEnablesHibernateFilterAndCleansUp() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        String token = jwtTokenService.generateToken(userId, tenantId, "TECHNICIAN").accessToken();

        mockMvc.perform(get("/api/v1/test/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.tenantId").value(tenantId.toString()))
                .andExpect(jsonPath("$.contextTenantId").value(tenantId.toString()))
                .andExpect(jsonPath("$.role").value("TECHNICIAN"))
                .andExpect(jsonPath("$.hibernateFilterActive").value(true));

        assertThat(TenantContext.getCurrentTenant()).isEmpty();
    }

    @Test
    void enforcesRoleBasedAccessControlWithProblemDetail() throws Exception {
        String attendantToken = jwtTokenService.generateToken(UUID.randomUUID(), UUID.randomUUID(), "ATTENDANT").accessToken();
        String ownerToken = jwtTokenService.generateToken(UUID.randomUUID(), UUID.randomUUID(), "OWNER").accessToken();

        mockMvc.perform(get("/api/v1/test/owner-only")
                        .header("Authorization", "Bearer " + attendantToken))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Forbidden"));

        mockMvc.perform(get("/api/v1/test/owner-only")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProbeConfig {

        @Bean
        ProbeController probeController(EntityManager entityManager) {
            return new ProbeController(entityManager);
        }
    }

    @RestController
    static class ProbeController {

        private final EntityManager entityManager;

        ProbeController(EntityManager entityManager) {
            this.entityManager = entityManager;
        }

        @GetMapping("/api/v1/public/ping")
        Map<String, Object> publicPing() {
            return Map.of("status", "ok");
        }

        @GetMapping("/api/v1/test/me")
        @Transactional(readOnly = true)
        Map<String, Object> me(@AuthenticationPrincipal AuthenticatedUser user) {
            Session session = entityManager.unwrap(Session.class);
            Filter enabledFilter = session.getEnabledFilter(TenantAwareEntity.TENANT_FILTER);
            return Map.of(
                    "userId", user.userId().toString(),
                    "tenantId", user.tenantId().toString(),
                    "contextTenantId", TenantContext.requireCurrentTenant().toString(),
                    "role", user.role(),
                    "hibernateFilterActive", enabledFilter != null
            );
        }

        @GetMapping("/api/v1/test/owner-only")
        @PreAuthorize("hasRole('OWNER')")
        Map<String, Object> ownerOnly() {
            return Map.of("allowed", true);
        }
    }
}
