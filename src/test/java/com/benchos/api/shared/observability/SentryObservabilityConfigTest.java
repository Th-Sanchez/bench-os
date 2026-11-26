package com.benchos.api.shared.observability;

import io.sentry.Sentry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class SentryObservabilityConfigTest {

    @Test
    void staysDisabledWhenDsnIsBlankAndDoesNotThrowOnCapture() {
        SentryProperties props = new SentryProperties("", "test", "benchos-api@test");
        assertThat(props.isConfigured()).isFalse();

        SentryObservabilityConfig config = new SentryObservabilityConfig(props);
        config.init();
        assertThat(Sentry.isEnabled()).isFalse();

        SentryReporter reporter = new SentryReporter();
        assertThatCode(() -> reporter.captureUnexpectedException(new RuntimeException("boom"), null))
                .doesNotThrowAnyException();
    }
}
