package com.benchos.api.integration.internal.service;

import com.benchos.api.integration.internal.config.WebhookProperties;
import com.benchos.api.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookSecurityUnitTest {

    @Test
    void signerGeneratesPrefixedSecretsAndDeterministicHmacHeader() {
        WebhookSigner signer = new WebhookSigner();
        String secret = signer.generateSecret();
        assertThat(secret).startsWith("whsec_").hasSize(54);

        String header1 = signer.computeSignatureHeader(secret, 1700000000L, "{\"event\":\"order.created\"}");
        String header2 = signer.computeSignatureHeader(secret, 1700000000L, "{\"event\":\"order.created\"}");
        assertThat(header1).isEqualTo(header2);
        assertThat(header1).startsWith("t=1700000000,v1=");
    }

    @Test
    void validatorRejectsHttpLocalhostAndPrivateIpsWhenPrivateTargetsDisallowed() throws Exception {
        WebhookProperties strictProps = new WebhookProperties();
        strictProps.setAllowPrivateTargets(false);
        WebhookUrlValidator validator = new WebhookUrlValidator(strictProps);

        assertThatThrownBy(() -> validator.validateAndParse("http://example.com/hook"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("https://");

        assertThatThrownBy(() -> validator.validateAndParse("https://localhost:8443/hook"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("localhost");

        assertThat(WebhookUrlValidator.isPrivateOrUnsafe(InetAddress.getByName("127.0.0.1"))).isTrue();
        assertThat(WebhookUrlValidator.isPrivateOrUnsafe(InetAddress.getByName("169.254.169.254"))).isTrue();
        assertThat(WebhookUrlValidator.isPrivateOrUnsafe(InetAddress.getByName("10.0.0.5"))).isTrue();
        assertThat(WebhookUrlValidator.isPrivateOrUnsafe(InetAddress.getByName("192.168.1.10"))).isTrue();
        assertThat(WebhookUrlValidator.isPrivateOrUnsafe(InetAddress.getByName("100.64.0.1"))).isTrue();
        assertThat(WebhookUrlValidator.isPrivateOrUnsafe(InetAddress.getByName("8.8.8.8"))).isFalse();
    }

    @Test
    void validatorAllowsLocalHttpWhenExplicitlyEnabledForDevAndTests() {
        WebhookProperties devProps = new WebhookProperties();
        devProps.setAllowPrivateTargets(true);
        WebhookUrlValidator validator = new WebhookUrlValidator(devProps);

        URI uri = validator.validateAndParse("http://127.0.0.1:9999/webhook");
        assertThat(uri.getHost()).isEqualTo("127.0.0.1");
        assertThat(uri.getPort()).isEqualTo(9999);
    }
}
