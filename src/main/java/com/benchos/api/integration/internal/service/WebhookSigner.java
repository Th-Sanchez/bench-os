package com.benchos.api.integration.internal.service;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Generates high-entropy signing secrets and computes Stripe-style HMAC-SHA256 signatures
 * ({@code t=<epoch>,v1=<hex>}) so downstream automations (n8n, Make, custom backends) can
 * verify authenticity and mitigate replay attacks.
 */
@Component
public class WebhookSigner {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    public String generateSecret() {
        byte[] bytes = new byte[24];
        SECURE_RANDOM.nextBytes(bytes);
        return "whsec_" + HEX.formatHex(bytes);
    }

    public String computeSignatureHeader(String secret, long timestampSeconds, String payloadJson) {
        String signedPayload = timestampSeconds + "." + payloadJson;
        String digest = computeHmacHex(secret, signedPayload);
        return "t=" + timestampSeconds + ",v1=" + digest;
    }

    public String computeHmacHex(String secret, String message) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(keySpec);
            byte[] raw = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return HEX.formatHex(raw);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to compute webhook HMAC-SHA256 signature", ex);
        }
    }
}
