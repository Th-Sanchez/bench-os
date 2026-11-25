package com.benchos.api.order.internal.web;

import com.benchos.api.shared.storage.StorageProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Pattern;

@RestController
public class PublicTrackingWebController {

    private static final String CSP_NONCE_PLACEHOLDER = "__CSP_NONCE__";
    private static final String ROBOTS_HEADER = "X-Robots-Tag";
    private static final String ROBOTS_VALUE = "noindex, nofollow, noarchive";
    private static final MediaType TEXT_HTML_UTF8 = new MediaType("text", "html", StandardCharsets.UTF_8);
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    );
    private static final String INVALID_LINK_HTML = """
            <!DOCTYPE html>
            <html lang="pt-BR">
            <head>
              <meta charset="UTF-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1.0" />
              <meta name="robots" content="noindex, nofollow, noarchive" />
              <meta name="referrer" content="no-referrer" />
              <title>Link inválido · BenchOS</title>
            </head>
            <body style="font-family:-apple-system,BlinkMacSystemFont,sans-serif;background:#F8FAFC;color:#0F172A;display:grid;place-items:center;min-height:100vh;margin:0;padding:24px;text-align:center;">
              <div>
                <h1 style="font-size:20px;margin:0 0 8px;">Link de acompanhamento inválido</h1>
                <p style="font-size:14px;color:#475569;margin:0;">Verifique o endereço enviado pela oficina e tente novamente.</p>
              </div>
            </body>
            </html>
            """;

    private final SecureRandom secureRandom = new SecureRandom();
    private final String trackingHtml;
    private final String receiptHtml;
    private final String storageOrigin;

    public PublicTrackingWebController(StorageProperties storageProperties) throws IOException {
        this.trackingHtml = new ClassPathResource("static/track.html")
                .getContentAsString(StandardCharsets.UTF_8);
        this.receiptHtml = new ClassPathResource("static/receipt.html")
                .getContentAsString(StandardCharsets.UTF_8);
        this.storageOrigin = extractOrigin(storageProperties.endpoint());
    }

    @GetMapping(value = "/track/{publicToken}", produces = "text/html;charset=UTF-8")
    public ResponseEntity<String> renderTrackingPage(
            @PathVariable String publicToken,
            HttpServletRequest request
    ) {
        return renderHardenedPage(publicToken, trackingHtml, request);
    }

    @GetMapping(value = "/track/{publicToken}/receipt", produces = "text/html;charset=UTF-8")
    public ResponseEntity<String> renderReceiptPage(
            @PathVariable String publicToken,
            HttpServletRequest request
    ) {
        return renderHardenedPage(publicToken, receiptHtml, request);
    }

    private ResponseEntity<String> renderHardenedPage(
            String publicToken,
            String htmlTemplate,
            HttpServletRequest request
    ) {
        if (publicToken == null || !UUID_PATTERN.matcher(publicToken).matches()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(TEXT_HTML_UTF8)
                    .cacheControl(CacheControl.noStore())
                    .header(ROBOTS_HEADER, ROBOTS_VALUE)
                    .header("Content-Security-Policy", buildCspHeader(null, request.isSecure()))
                    .body(INVALID_LINK_HTML);
        }

        String nonce = generateNonce();
        String renderedHtml = htmlTemplate.replace(CSP_NONCE_PLACEHOLDER, nonce);

        return ResponseEntity.ok()
                .contentType(TEXT_HTML_UTF8)
                .cacheControl(CacheControl.noStore())
                .header(ROBOTS_HEADER, ROBOTS_VALUE)
                .header("Content-Security-Policy", buildCspHeader(nonce, request.isSecure()))
                .body(renderedHtml);
    }

    private String generateNonce() {
        byte[] bytes = new byte[16];
        secureRandom.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private String buildCspHeader(String nonce, boolean isSecure) {
        String scriptSrc = (nonce != null) ? "'nonce-" + nonce + "'" : "'none'";
        String imgSrc = storageOrigin.isEmpty() ? "'self' data: https:" : "'self' data: " + storageOrigin;
        StringBuilder csp = new StringBuilder()
                .append("default-src 'none'; ")
                .append("script-src ").append(scriptSrc).append("; ")
                .append("style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; ")
                .append("font-src https://fonts.gstatic.com; ")
                .append("img-src ").append(imgSrc).append("; ")
                .append("connect-src 'self'; ")
                .append("form-action 'none'; ")
                .append("frame-ancestors 'none'; ")
                .append("base-uri 'none'; ")
                .append("object-src 'none'");
        if (isSecure) {
            csp.append("; upgrade-insecure-requests");
        }
        return csp.toString();
    }

    private static String extractOrigin(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return "";
        }
        try {
            URI uri = URI.create(endpoint.trim());
            if (uri.getScheme() == null || uri.getHost() == null) {
                return "";
            }
            StringBuilder origin = new StringBuilder()
                    .append(uri.getScheme())
                    .append("://")
                    .append(uri.getHost());
            if (uri.getPort() != -1) {
                origin.append(':').append(uri.getPort());
            }
            return origin.toString();
        } catch (IllegalArgumentException ex) {
            return "";
        }
    }
}
