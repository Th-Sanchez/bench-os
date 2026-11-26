package com.benchos.api.shared.web;

import com.benchos.api.shared.exception.BusinessRuleException;
import com.benchos.api.shared.exception.ConflictException;
import com.benchos.api.shared.exception.ResourceNotFoundException;
import com.benchos.api.shared.observability.SentryReporter;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PessimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Global exception handler returning RFC 7807 Problem Details responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final SentryReporter sentryReporter;

    public GlobalExceptionHandler(SentryReporter sentryReporter) {
        this.sentryReporter = sentryReporter;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleResourceNotFound(ResourceNotFoundException ex) {
        return buildProblem(HttpStatus.NOT_FOUND, "Resource Not Found", "not-found", ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException ex) {
        return buildProblem(HttpStatus.CONFLICT, "Resource Conflict", "conflict", ex.getMessage());
    }

    @ExceptionHandler({
            ConcurrencyFailureException.class,
            OptimisticLockException.class,
            PessimisticLockException.class
    })
    public ProblemDetail handleConcurrencyConflict(Exception ex) {
        log.warn("Concurrent modification conflict detected: {}", ex.getMessage());
        return buildProblem(
                HttpStatus.CONFLICT,
                "Concurrent Modification",
                "concurrent-modification",
                "This record was updated concurrently by another request. Please refresh and try again."
        );
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ProblemDetail handleBusinessRule(BusinessRuleException ex) {
        return buildProblem(HttpStatusCode.valueOf(422), "Business Rule Violation", "business-rule-violation", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        ProblemDetail problem = buildProblem(
                HttpStatus.BAD_REQUEST,
                "Validation Failed",
                "validation-error",
                "One or more fields have invalid values"
        );
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        problem.setProperty("errors", fieldErrors);
        return problem;
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException ex) {
        String detail = ex instanceof BadCredentialsException
                ? "Invalid email or password"
                : "Authentication is required";
        return buildProblem(HttpStatus.UNAUTHORIZED, "Unauthorized", "unauthorized", detail);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return buildProblem(HttpStatus.FORBIDDEN, "Forbidden", "forbidden", "You do not have permission to perform this action");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception while processing request", ex);
        sentryReporter.captureUnexpectedException(ex, request);
        return buildProblem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                "internal-error",
                "An unexpected error occurred"
        );
    }

    private ProblemDetail buildProblem(HttpStatusCode status, String title, String errorCode, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("https://benchos.com/errors/" + errorCode));
        problem.setProperty("timestamp", Instant.now());
        String requestId = MDC.get(RequestCorrelationFilter.REQUEST_ID_MDC_KEY);
        if (requestId != null && !requestId.isBlank()) {
            problem.setProperty("requestId", requestId);
        }
        return problem;
    }
}
