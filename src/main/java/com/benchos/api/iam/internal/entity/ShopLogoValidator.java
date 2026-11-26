package com.benchos.api.iam.internal.entity;

import com.benchos.api.shared.exception.BusinessRuleException;

import java.util.Base64;

/**
 * Strictly validates raster workshop logos (PNG, JPEG, WebP) encoded as Data URLs.
 * Disallows SVG/HTML payloads to prevent XSS in public tracking/receipt views.
 */
public final class ShopLogoValidator {

    public static final int MAX_DECODED_BYTES = 200 * 1024; // 200 KB

    private static final String PNG_PREFIX = "data:image/png;base64,";
    private static final String JPEG_PREFIX = "data:image/jpeg;base64,";
    private static final String WEBP_PREFIX = "data:image/webp;base64,";

    private ShopLogoValidator() {
    }

    public static String normalizeAndValidate(String rawDataUrl) {
        if (rawDataUrl == null) {
            return null;
        }
        String trimmed = rawDataUrl.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        String prefix;
        if (trimmed.startsWith(PNG_PREFIX)) {
            prefix = PNG_PREFIX;
        } else if (trimmed.startsWith(JPEG_PREFIX)) {
            prefix = JPEG_PREFIX;
        } else if (trimmed.startsWith(WEBP_PREFIX)) {
            prefix = WEBP_PREFIX;
        } else {
            throw new BusinessRuleException("Logo must be a valid PNG, JPEG or WebP image");
        }

        String base64Body = trimmed.substring(prefix.length());
        if (base64Body.isEmpty() || base64Body.length() > 300_000) {
            throw new BusinessRuleException("Logo image exceeds maximum allowed size (200 KB)");
        }

        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(base64Body);
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("Logo contains invalid base64 image data");
        }

        if (decoded.length == 0 || decoded.length > MAX_DECODED_BYTES) {
            throw new BusinessRuleException("Logo image exceeds maximum allowed size (200 KB)");
        }

        if (PNG_PREFIX.equals(prefix) && !isPng(decoded)) {
            throw new BusinessRuleException("Logo content does not match PNG signature");
        }
        if (JPEG_PREFIX.equals(prefix) && !isJpeg(decoded)) {
            throw new BusinessRuleException("Logo content does not match JPEG signature");
        }
        if (WEBP_PREFIX.equals(prefix) && !isWebp(decoded)) {
            throw new BusinessRuleException("Logo content does not match WebP signature");
        }

        return prefix + Base64.getEncoder().encodeToString(decoded);
    }

    private static boolean isPng(byte[] data) {
        return data.length >= 8
                && (data[0] & 0xFF) == 0x89
                && data[1] == 0x50
                && data[2] == 0x4E
                && data[3] == 0x47
                && data[4] == 0x0D
                && data[5] == 0x0A
                && data[6] == 0x1A
                && data[7] == 0x0A;
    }

    private static boolean isJpeg(byte[] data) {
        return data.length >= 3
                && (data[0] & 0xFF) == 0xFF
                && (data[1] & 0xFF) == 0xD8
                && (data[2] & 0xFF) == 0xFF;
    }

    private static boolean isWebp(byte[] data) {
        return data.length >= 12
                && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P';
    }
}
