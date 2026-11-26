package com.benchos.api.shared.observability;

import io.sentry.Sentry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
@EnableConfigurationProperties(SentryProperties.class)
public class SentryObservabilityConfig {

    private static final Logger log = LoggerFactory.getLogger(SentryObservabilityConfig.class);

    private final SentryProperties properties;

    public SentryObservabilityConfig(SentryProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void init() {
        if (!properties.isConfigured()) {
            log.info("Sentry observability disabled (SENTRY_DSN not configured)");
            return;
        }

        Sentry.init(options -> {
            options.setDsn(properties.dsn().trim());
            options.setEnvironment(
                    properties.environment() != null && !properties.environment().isBlank()
                            ? properties.environment().trim()
                            : "production"
            );
            if (properties.release() != null && !properties.release().isBlank()) {
                options.setRelease(properties.release().trim());
            }
            options.setSendDefaultPii(false);
            options.setAttachStacktrace(true);
            options.setBeforeSend((event, hint) -> {
                if (event.getRequest() != null && event.getRequest().getHeaders() != null) {
                    Map<String, String> cleanHeaders = new LinkedHashMap<>();
                    event.getRequest().getHeaders().forEach((k, v) -> {
                        if (k == null) return;
                        String lower = k.toLowerCase();
                        if (!lower.equals("authorization") && !lower.equals("cookie") && !lower.equals("set-cookie")) {
                            cleanHeaders.put(k, v);
                        }
                    });
                    event.getRequest().setHeaders(cleanHeaders);
                }
                return event;
            });
        });

        log.info("Sentry observability enabled for environment={}", properties.environment());
    }

    @PreDestroy
    void shutdown() {
        if (Sentry.isEnabled()) {
            Sentry.flush(2_000L);
            Sentry.close();
        }
    }
}
