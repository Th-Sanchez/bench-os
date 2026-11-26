package com.benchos.api.shared.pix;

import com.benchos.api.shared.exception.BusinessRuleException;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Generates Banco Central do Brasil compliant static Pix BR Codes (EMV QRCPS-MPM)
 * and compact SVG QR codes without external payment gateways.
 */
public final class PixBrCodeGenerator {

    private static final String PIX_GUI = "br.gov.bcb.pix";
    private static final int MAX_PIX_KEY_LENGTH = 77;
    private static final int MAX_MERCHANT_NAME_LENGTH = 25;
    private static final int MAX_MERCHANT_CITY_LENGTH = 15;
    private static final int MAX_TXID_LENGTH = 25;

    private PixBrCodeGenerator() {
    }

    public static String normalizeAndValidatePixKey(String rawKey) {
        if (rawKey == null) {
            return null;
        }
        String trimmed = rawKey.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > MAX_PIX_KEY_LENGTH) {
            throw new BusinessRuleException("Pix key must be at most " + MAX_PIX_KEY_LENGTH + " characters");
        }
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (Character.isWhitespace(c) || c < 0x20 || c > 0x7E) {
                throw new BusinessRuleException("Pix key must contain only valid non-whitespace ASCII characters");
            }
        }
        return trimmed;
    }

    public static String generate(
            String pixKey,
            String merchantName,
            String merchantCity,
            BigDecimal amount,
            String txid
    ) {
        String validKey = normalizeAndValidatePixKey(pixKey);
        if (validKey == null) {
            throw new BusinessRuleException("Pix key is required to generate BR Code");
        }

        String normalizedName = sanitizeAscii(merchantName, MAX_MERCHANT_NAME_LENGTH, "OFICINA");
        String normalizedCity = sanitizeAscii(merchantCity, MAX_MERCHANT_CITY_LENGTH, "BRASIL");
        String normalizedTxid = sanitizeTxid(txid);

        String merchantAccountInfo = tlv("00", PIX_GUI) + tlv("01", validKey);
        String additionalData = tlv("05", normalizedTxid);

        StringBuilder payload = new StringBuilder(160);
        payload.append(tlv("00", "01"));
        payload.append(tlv("26", merchantAccountInfo));
        payload.append(tlv("52", "0000"));
        payload.append(tlv("53", "986"));
        if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal scaled = amount.setScale(2, RoundingMode.HALF_UP);
            payload.append(tlv("54", String.format(Locale.US, "%.2f", scaled)));
        }
        payload.append(tlv("58", "BR"));
        payload.append(tlv("59", normalizedName));
        payload.append(tlv("60", normalizedCity));
        payload.append(tlv("62", additionalData));
        payload.append("6304");

        int crc = crc16Ccitt(payload.toString().getBytes(StandardCharsets.UTF_8));
        payload.append(String.format(Locale.US, "%04X", crc));
        return payload.toString();
    }

    public static String renderQrSvg(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 2);

            BitMatrix matrix = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 0, 0, hints);
            int width = matrix.getWidth();
            int height = matrix.getHeight();

            StringBuilder path = new StringBuilder(width * height / 2);
            for (int y = 0; y < height; y++) {
                int x = 0;
                while (x < width) {
                    if (matrix.get(x, y)) {
                        int runStart = x;
                        while (x < width && matrix.get(x, y)) {
                            x++;
                        }
                        int runLength = x - runStart;
                        path.append('M').append(runStart).append(' ').append(y)
                                .append('h').append(runLength)
                                .append("v1h-").append(runLength).append('z');
                    } else {
                        x++;
                    }
                }
            }

            return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 "
                    + width + " " + height
                    + "\" shape-rendering=\"crispEdges\" role=\"img\" aria-label=\"QR Code Pix\">"
                    + "<rect width=\"100%\" height=\"100%\" fill=\"#FFFFFF\"/>"
                    + "<path d=\"" + path + "\" fill=\"#0F172A\"/>"
                    + "</svg>";
        } catch (WriterException ex) {
            throw new IllegalStateException("Failed to render Pix QR Code SVG", ex);
        }
    }

    static int crc16Ccitt(byte[] bytes) {
        int crc = 0xFFFF;
        for (byte b : bytes) {
            crc ^= (b & 0xFF) << 8;
            for (int bit = 0; bit < 8; bit++) {
                if ((crc & 0x8000) != 0) {
                    crc = ((crc << 1) ^ 0x1021) & 0xFFFF;
                } else {
                    crc = (crc << 1) & 0xFFFF;
                }
            }
        }
        return crc & 0xFFFF;
    }

    private static String tlv(String id, String value) {
        byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
        if (utf8.length > 99) {
            throw new BusinessRuleException("EMV field " + id + " exceeds maximum length of 99 bytes");
        }
        return id + String.format(Locale.US, "%02d", utf8.length) + value;
    }

    private static String sanitizeAscii(String raw, int maxLength, String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String decomposed = Normalizer.normalize(raw.trim(), Normalizer.Form.NFD);
        StringBuilder sb = new StringBuilder(Math.min(decomposed.length(), maxLength));
        boolean lastWasSpace = false;
        for (int i = 0; i < decomposed.length() && sb.length() < maxLength; i++) {
            char c = decomposed.charAt(i);
            if (Character.getType(c) == Character.NON_SPACING_MARK) {
                continue;
            }
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                sb.append(c);
                lastWasSpace = false;
            } else if (c == ' ' || c == '-' || c == '.' || c == '/') {
                if (!sb.isEmpty() && !lastWasSpace) {
                    sb.append(c == ' ' ? ' ' : c);
                    lastWasSpace = (c == ' ');
                }
            }
        }
        String cleaned = sb.toString().trim();
        return cleaned.isEmpty() ? fallback : cleaned;
    }

    private static String sanitizeTxid(String rawTxid) {
        if (rawTxid == null || rawTxid.isBlank()) {
            return "***";
        }
        String decomposed = Normalizer.normalize(rawTxid.trim(), Normalizer.Form.NFD);
        StringBuilder sb = new StringBuilder(MAX_TXID_LENGTH);
        for (int i = 0; i < decomposed.length() && sb.length() < MAX_TXID_LENGTH; i++) {
            char c = decomposed.charAt(i);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                sb.append(c);
            }
        }
        return sb.isEmpty() ? "***" : sb.toString();
    }
}
