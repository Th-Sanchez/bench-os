package com.benchos.api.shared.ratelimit;

import com.benchos.api.shared.web.RequestCorrelationFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

/**
 * Enforces HTTP rate limiting on unauthenticated public and authentication surfaces to mitigate
 * brute-force attacks, credential stuffing, account creation abuse, and tracking endpoint flooding.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    public static final String HEADER_LIMIT = "X-RateLimit-Limit";
    public static final String HEADER_REMAINING = "X-RateLimit-Remaining";

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final RateLimiter rateLimiter;
    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(
            RateLimiter rateLimiter,
            RateLimitProperties properties,
            ObjectMapper objectMapper
    ) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.isEnabled() || HttpMethod.OPTIONS.matches(request.getMethod());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String method = request.getMethod();
        String path = normalizePath(request);
        MatchedPolicy matched = matchPolicy(method, path);
        if (matched == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = resolveClientIp(request);
        RateLimitDecision ipDecision = rateLimiter.tryConsume(
                matched.ipRule().name() + ":" + clientIp,
                matched.ipRule()
        );
        if (!ipDecision.allowed()) {
            logBlocked(matched.ipRule().name(), method, path, clientIp, ipDecision.retryAfterSeconds());
            writeTooManyRequests(response, ipDecision);
            return;
        }

        HttpServletRequest downstreamRequest = request;
        RateLimitDecision effectiveDecision = ipDecision;

        if (matched.accountRule() != null && isJsonRequest(request)) {
            CachedBodyHttpServletRequest wrapped = CachedBodyHttpServletRequest.capture(request);
            downstreamRequest = wrapped;

            Optional<String> normalizedEmail = extractNormalizedEmail(wrapped);
            if (normalizedEmail.isPresent()) {
                RateLimitDecision accountDecision = rateLimiter.tryConsume(
                        matched.accountRule().name() + ":" + normalizedEmail.get(),
                        matched.accountRule()
                );
                if (!accountDecision.allowed()) {
                    logBlocked(matched.accountRule().name(), method, path, clientIp, accountDecision.retryAfterSeconds());
                    writeTooManyRequests(response, accountDecision);
                    return;
                }
                if (accountDecision.remaining() < effectiveDecision.remaining()) {
                    effectiveDecision = accountDecision;
                }
            }
        }

        response.setHeader(HEADER_LIMIT, String.valueOf(effectiveDecision.limit()));
        response.setHeader(HEADER_REMAINING, String.valueOf(effectiveDecision.remaining()));
        filterChain.doFilter(downstreamRequest, response);
    }

    private MatchedPolicy matchPolicy(String method, String path) {
        if (HttpMethod.POST.matches(method)) {
            if ("/api/v1/auth/login".equals(path)) {
                return new MatchedPolicy(
                        properties.login().ipRule("auth-login"),
                        properties.login().accountRule("auth-login")
                );
            }
            if ("/api/v1/auth/register".equals(path)) {
                return new MatchedPolicy(properties.register().ipRule("auth-register"), null);
            }
            if ("/api/v1/auth/forgot-password".equals(path)) {
                return new MatchedPolicy(
                        properties.forgotPassword().ipRule("auth-forgot-password"),
                        properties.forgotPassword().accountRule("auth-forgot-password")
                );
            }
            if ("/api/v1/auth/reset-password".equals(path)) {
                return new MatchedPolicy(
                        properties.resetPassword().ipRule("auth-reset-password"),
                        properties.resetPassword().accountRule("auth-reset-password")
                );
            }
        }

        if (isPublicSurface(path)) {
            boolean isRead = HttpMethod.GET.matches(method) || HttpMethod.HEAD.matches(method);
            RateLimitRule rule = isRead
                    ? properties.publicRead().ipRule("public-read")
                    : properties.publicWrite().ipRule("public-write");
            return new MatchedPolicy(rule, null);
        }

        return null;
    }

    private static boolean isPublicSurface(String path) {
        return "/api/v1/public".equals(path)
                || path.startsWith("/api/v1/public/")
                || "/track".equals(path)
                || path.startsWith("/track/");
    }

    private static String normalizePath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null || uri.isEmpty()) {
            return "/";
        }
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            uri = uri.substring(contextPath.length());
        }
        while (uri.length() > 1 && uri.endsWith("/")) {
            uri = uri.substring(0, uri.length() - 1);
        }
        return uri;
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (properties.isTrustForwardedHeaders()) {
            String forwardedFor = request.getHeader("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                String firstHop = forwardedFor.split(",")[0].trim();
                if (!firstHop.isEmpty()) {
                    return firstHop;
                }
            }
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isBlank()) {
                return realIp.trim();
            }
        }
        String remoteAddr = request.getRemoteAddr();
        return (remoteAddr == null || remoteAddr.isBlank()) ? "unknown" : remoteAddr.trim();
    }

    private static boolean isJsonRequest(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null && contentType.toLowerCase(Locale.ROOT).contains("json");
    }

    private Optional<String> extractNormalizedEmail(CachedBodyHttpServletRequest request) {
        if (!request.isFullyCached() || request.cachedBodyBytes().length == 0) {
            return Optional.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(request.cachedBodyBytes());
            if (root == null || !root.isObject()) {
                return Optional.empty();
            }
            JsonNode emailNode = root.get("email");
            if (emailNode == null || !emailNode.isString()) {
                return Optional.empty();
            }
            String normalized = emailNode.asString().trim().toLowerCase(Locale.ROOT);
            if (normalized.isEmpty() || normalized.length() > 254) {
                return Optional.empty();
            }
            return Optional.of(normalized);
        } catch (RuntimeException ex) {
            // Let Spring MVC handle malformed JSON payloads with a standard 400 response
            return Optional.empty();
        }
    }

    private void logBlocked(String ruleName, String method, String path, String clientIp, long retryAfterSeconds) {
        log.warn(
                "Rate limit exceeded: rule={} method={} path={} ip={} retryAfterSeconds={}",
                ruleName,
                method,
                path,
                clientIp,
                retryAfterSeconds
        );
    }

    private void writeTooManyRequests(HttpServletResponse response, RateLimitDecision decision) throws IOException {
        String detail = "Too many requests. Please try again in " + decision.retryAfterSeconds() + " seconds.";
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, detail);
        problem.setTitle("Too Many Requests");
        problem.setType(URI.create("https://benchos.com/errors/rate-limit-exceeded"));
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("retryAfterSeconds", decision.retryAfterSeconds());

        String requestId = MDC.get(RequestCorrelationFilter.REQUEST_ID_MDC_KEY);
        if (requestId != null && !requestId.isBlank()) {
            problem.setProperty("requestId", requestId);
        }

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(decision.retryAfterSeconds()));
        response.setHeader(HEADER_LIMIT, String.valueOf(decision.limit()));
        response.setHeader(HEADER_REMAINING, "0");
        objectMapper.writeValue(response.getOutputStream(), problem);
    }

    private record MatchedPolicy(
            RateLimitRule ipRule,
            RateLimitRule accountRule
    ) {
    }
}
