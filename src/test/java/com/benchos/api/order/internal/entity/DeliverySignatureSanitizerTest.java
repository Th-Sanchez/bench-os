package com.benchos.api.order.internal.entity;

import com.benchos.api.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeliverySignatureSanitizerTest {

    @Test
    void returnsNullForNullOrBlankInput() {
        assertThat(DeliverySignatureSanitizer.sanitize(null)).isNull();
        assertThat(DeliverySignatureSanitizer.sanitize("   ")).isNull();
    }

    @Test
    void canonicalizesValidSvgSignatureWithMultipleStrokes() {
        String raw = """
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 520 170" fill="none">
                  <path d="M10 20 L40 50 L80 35" stroke="#000" stroke-width="3"/>
                  <path d="M90.5 40.2L120.1 60.8" stroke="#111"/>
                </svg>
                """;

        String sanitized = DeliverySignatureSanitizer.sanitize(raw);

        assertThat(sanitized).isEqualTo(
                "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 520 170\" fill=\"none\">"
                        + "<path d=\"M10 20 L40 50 L80 35\" stroke=\"#0F172A\" stroke-width=\"2.5\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/>"
                        + "<path d=\"M90.5 40.2L120.1 60.8\" stroke=\"#0F172A\" stroke-width=\"2.5\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/>"
                        + "</svg>"
        );
    }

    @Test
    void rejectsForbiddenSvgPayloadsAndMalformedInputs() {
        assertThatThrownBy(() -> DeliverySignatureSanitizer.sanitize(
                "<svg viewBox=\"0 0 200 100\"><script>alert(1)</script><path d=\"M1 1L10 10\"/></svg>"
        )).isInstanceOf(BusinessRuleException.class);

        assertThatThrownBy(() -> DeliverySignatureSanitizer.sanitize(
                "<svg viewBox=\"0 0 200 100\" onload=\"alert(1)\"><path d=\"M1 1L10 10\"/></svg>"
        )).isInstanceOf(BusinessRuleException.class);

        assertThatThrownBy(() -> DeliverySignatureSanitizer.sanitize(
                "<svg viewBox=\"0 0 200 100\"><foreignObject>evil</foreignObject></svg>"
        )).isInstanceOf(BusinessRuleException.class);

        assertThatThrownBy(() -> DeliverySignatureSanitizer.sanitize(
                "<svg viewBox=\"0 0 200 100\"></svg>"
        )).isInstanceOf(BusinessRuleException.class);

        assertThatThrownBy(() -> DeliverySignatureSanitizer.sanitize(
                "<svg viewBox=\"0 0 200 100\"><path d=\"M1 1; alert(1)\"/></svg>"
        )).isInstanceOf(BusinessRuleException.class);

        assertThatThrownBy(() -> DeliverySignatureSanitizer.sanitize(
                "<svg><path d=\"M1 1L10 10\"/></svg>"
        )).isInstanceOf(BusinessRuleException.class);
    }
}
