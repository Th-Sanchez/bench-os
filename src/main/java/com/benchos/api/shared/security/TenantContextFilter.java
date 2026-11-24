package com.benchos.api.shared.security;

import com.benchos.api.shared.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Binds the authenticated user's tenantId into {@link TenantContext} and SLF4J {@link MDC}
 * for the duration of the HTTP request and guarantees cleanup in a finally block so pooled
 * servlet threads never leak tenant state.
 */
public class TenantContextFilter extends OncePerRequestFilter {

    public static final String TENANT_ID_MDC_KEY = "tenantId";
    public static final String USER_ID_MDC_KEY = "userId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null
                    && authentication.isAuthenticated()
                    && authentication.getPrincipal() instanceof AuthenticatedUser user) {
                TenantContext.setCurrentTenant(user.tenantId());
                MDC.put(TENANT_ID_MDC_KEY, user.tenantId().toString());
                MDC.put(USER_ID_MDC_KEY, user.userId().toString());
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            MDC.remove(TENANT_ID_MDC_KEY);
            MDC.remove(USER_ID_MDC_KEY);
        }
    }
}
