package com.benchos.api.shared.security;

import com.benchos.api.shared.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantContextFilterTest {

    private final TenantContextFilter filter = new TenantContextFilter();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
        MDC.clear();
    }

    @Test
    void bindsTenantDuringRequestAndClearsAfterCompletion() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        AuthenticatedUser principal = new AuthenticatedUser(userId, tenantId, "OWNER");
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, List.of(new SimpleGrantedAuthority("ROLE_OWNER"))
                )
        );

        AtomicReference<UUID> observedTenant = new AtomicReference<>();
        AtomicReference<String> observedTenantMdc = new AtomicReference<>();
        AtomicReference<String> observedUserMdc = new AtomicReference<>();
        FilterChain chain = (req, res) -> {
            observedTenant.set(TenantContext.requireCurrentTenant());
            observedTenantMdc.set(MDC.get(TenantContextFilter.TENANT_ID_MDC_KEY));
            observedUserMdc.set(MDC.get(TenantContextFilter.USER_ID_MDC_KEY));
        };

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertThat(observedTenant.get()).isEqualTo(tenantId);
        assertThat(observedTenantMdc.get()).isEqualTo(tenantId.toString());
        assertThat(observedUserMdc.get()).isEqualTo(userId.toString());
        assertThat(TenantContext.getCurrentTenant()).isEmpty();
        assertThat(MDC.get(TenantContextFilter.TENANT_ID_MDC_KEY)).isNull();
        assertThat(MDC.get(TenantContextFilter.USER_ID_MDC_KEY)).isNull();
    }

    @Test
    void clearsTenantContextEvenWhenDownstreamThrowsException() {
        UUID tenantId = UUID.randomUUID();
        AuthenticatedUser principal = new AuthenticatedUser(UUID.randomUUID(), tenantId, "TECHNICIAN");
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, List.of(new SimpleGrantedAuthority("ROLE_TECHNICIAN"))
                )
        );

        FilterChain failingChain = (req, res) -> {
            throw new ServletException("boom");
        };

        assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), failingChain))
                .isInstanceOf(ServletException.class);

        assertThat(TenantContext.getCurrentTenant()).isEmpty();
    }

    @Test
    void leavesTenantContextEmptyForUnauthenticatedRequests() throws Exception {
        AtomicReference<Boolean> hadTenant = new AtomicReference<>(true);
        FilterChain chain = (req, res) -> hadTenant.set(TenantContext.getCurrentTenant().isPresent());

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertThat(hadTenant.get()).isFalse();
        assertThat(TenantContext.getCurrentTenant()).isEmpty();
    }
}
