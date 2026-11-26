package com.benchos.api.shared.observability;

import com.benchos.api.shared.security.AuthenticatedUser;
import com.benchos.api.shared.tenant.TenantContext;
import com.benchos.api.shared.web.RequestCorrelationFilter;
import io.sentry.Sentry;
import io.sentry.protocol.User;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Captures unexpected server errors (5xx) in Sentry with non-PII correlation metadata
 * (requestId, tenantId, operator role, HTTP method & path).
 */
@Component
public class SentryReporter {

    public void captureUnexpectedException(Throwable exception, HttpServletRequest request) {
        if (exception == null || !Sentry.isEnabled()) {
            return;
        }

        String requestId = MDC.get(RequestCorrelationFilter.REQUEST_ID_MDC_KEY);
        UUID tenantId = TenantContext.getCurrentTenant().orElse(null);
        AuthenticatedUser currentUser = resolveCurrentUser();

        Sentry.withScope(scope -> {
            scope.setTag("error.source", "api");
            if (requestId != null && !requestId.isBlank()) {
                scope.setTag("request_id", requestId);
            }
            if (tenantId != null) {
                scope.setTag("tenant.id", tenantId.toString());
            }
            if (request != null) {
                scope.setTag("http.method", request.getMethod());
                scope.setTag("http.path", request.getRequestURI());
            }
            if (currentUser != null) {
                User sentryUser = new User();
                sentryUser.setId(currentUser.userId().toString());
                scope.setUser(sentryUser);
                scope.setTag("user.role", currentUser.role());
                if (tenantId == null && currentUser.tenantId() != null) {
                    scope.setTag("tenant.id", currentUser.tenantId().toString());
                }
            }
            Sentry.captureException(exception);
        });
    }

    private static AuthenticatedUser resolveCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        return null;
    }
}
