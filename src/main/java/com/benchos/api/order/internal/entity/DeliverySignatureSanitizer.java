package com.benchos.api.order.internal.entity;

import com.benchos.api.shared.exception.BusinessRuleException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates and canonicalizes customer delivery signature SVGs into a strictly safe,
 * minimal vector format containing only a numeric viewBox and sanitized {@code <path>} strokes.
 */
final class DeliverySignatureSanitizer {

    static final int MAX_RAW_SVG_LENGTH = 32_768;
    private static final int MAX_PATHS = 200;
    private static final int MAX_PATH_DATA_LENGTH = 8_192;
    private static final double MAX_VIEWBOX_DIMENSION = 4_000.0;

    private static final Pattern FORBIDDEN_PATTERN = Pattern.compile(
            "(?i)(<\\s*script|<\\s*foreignObject|<\\s*iframe|<\\s*object|<\\s*embed|<\\s*image|javascript:|data:|on[a-z]+\\s*=|href\\s*=|style\\s*=)"
    );
    private static final Pattern SVG_TAG_PATTERN = Pattern.compile(
            "(?is)^\\s*<svg\\b([^>]*)>(.*)</svg>\\s*$"
    );
    private static final Pattern VIEWBOX_ATTR_PATTERN = Pattern.compile(
            "(?i)\\bviewBox\\s*=\\s*[\"']\\s*(-?\\d+(?:\\.\\d+)?)\\s+(-?\\d+(?:\\.\\d+)?)\\s+(\\d+(?:\\.\\d+)?)\\s+(\\d+(?:\\.\\d+)?)\\s*[\"']"
    );
    private static final Pattern PATH_TAG_PATTERN = Pattern.compile(
            "(?is)<path\\b([^>]*)/>"
    );
    private static final Pattern PATH_D_ATTR_PATTERN = Pattern.compile(
            "(?i)\\bd\\s*=\\s*[\"']([^\"']+)[\"']"
    );
    private static final Pattern SAFE_PATH_DATA_PATTERN = Pattern.compile(
            "^[0-9eE.,\\sMmLlQqCcSsTtAaHhVvZz+-]+$"
    );
    private static final Pattern HAS_DRAW_COMMAND_PATTERN = Pattern.compile(
            "[Mm]\\s*-?\\d"
    );

    private DeliverySignatureSanitizer() {
    }

    static String sanitize(String rawSvg) {
        if (rawSvg == null || rawSvg.isBlank()) {
            return null;
        }
        String trimmed = rawSvg.trim();
        if (trimmed.length() > MAX_RAW_SVG_LENGTH) {
            throw new BusinessRuleException("Delivery signature SVG exceeds maximum allowed size");
        }
        if (FORBIDDEN_PATTERN.matcher(trimmed).find()) {
            throw new BusinessRuleException("Delivery signature SVG contains forbidden content");
        }

        Matcher svgMatcher = SVG_TAG_PATTERN.matcher(trimmed);
        if (!svgMatcher.matches()) {
            throw new BusinessRuleException("Delivery signature must be a valid SVG element");
        }

        String svgAttrs = svgMatcher.group(1);
        String innerContent = svgMatcher.group(2);

        Matcher viewBoxMatcher = VIEWBOX_ATTR_PATTERN.matcher(svgAttrs);
        if (!viewBoxMatcher.find()) {
            throw new BusinessRuleException("Delivery signature SVG must include a numeric viewBox");
        }

        double minX = parseFinite(viewBoxMatcher.group(1));
        double minY = parseFinite(viewBoxMatcher.group(2));
        double width = parseFinite(viewBoxMatcher.group(3));
        double height = parseFinite(viewBoxMatcher.group(4));
        if (width <= 0 || height <= 0 || width > MAX_VIEWBOX_DIMENSION || height > MAX_VIEWBOX_DIMENSION) {
            throw new BusinessRuleException("Delivery signature SVG viewBox dimensions are out of range");
        }

        String cleanedInner = innerContent.trim();
        Matcher pathMatcher = PATH_TAG_PATTERN.matcher(cleanedInner);
        List<String> sanitizedPaths = new ArrayList<>();
        int lastEnd = 0;
        while (pathMatcher.find()) {
            if (!cleanedInner.substring(lastEnd, pathMatcher.start()).isBlank()) {
                throw new BusinessRuleException("Delivery signature SVG may only contain <path/> elements");
            }
            lastEnd = pathMatcher.end();

            if (sanitizedPaths.size() >= MAX_PATHS) {
                throw new BusinessRuleException("Delivery signature SVG contains too many strokes");
            }
            String pathAttrs = pathMatcher.group(1);
            Matcher dMatcher = PATH_D_ATTR_PATTERN.matcher(pathAttrs);
            if (!dMatcher.find()) {
                throw new BusinessRuleException("Delivery signature path is missing stroke coordinates");
            }
            String d = dMatcher.group(1).trim().replaceAll("\\s+", " ");
            if (d.isEmpty() || d.length() > MAX_PATH_DATA_LENGTH
                    || !SAFE_PATH_DATA_PATTERN.matcher(d).matches()
                    || !HAS_DRAW_COMMAND_PATTERN.matcher(d).find()) {
                throw new BusinessRuleException("Delivery signature path contains invalid coordinates");
            }
            sanitizedPaths.add(d);
        }

        if (!cleanedInner.substring(lastEnd).isBlank()) {
            throw new BusinessRuleException("Delivery signature SVG may only contain <path/> elements");
        }
        if (sanitizedPaths.isEmpty()) {
            throw new BusinessRuleException("Delivery signature SVG must contain at least one stroke");
        }

        StringBuilder canonical = new StringBuilder();
        canonical.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"")
                .append(formatNumber(minX)).append(' ')
                .append(formatNumber(minY)).append(' ')
                .append(formatNumber(width)).append(' ')
                .append(formatNumber(height))
                .append("\" fill=\"none\">");
        for (String d : sanitizedPaths) {
            canonical.append("<path d=\"")
                    .append(d)
                    .append("\" stroke=\"#0F172A\" stroke-width=\"2.5\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/>");
        }
        canonical.append("</svg>");
        return canonical.toString();
    }

    private static double parseFinite(String raw) {
        try {
            double val = Double.parseDouble(raw);
            if (!Double.isFinite(val)) {
                throw new BusinessRuleException("Delivery signature viewBox contains non-finite numbers");
            }
            return val;
        } catch (NumberFormatException ex) {
            throw new BusinessRuleException("Delivery signature viewBox contains invalid numbers");
        }
    }

    private static String formatNumber(double val) {
        if (Math.floor(val) == val) {
            return String.valueOf((long) val);
        }
        return String.format(Locale.ROOT, "%.2f", val);
    }
}
