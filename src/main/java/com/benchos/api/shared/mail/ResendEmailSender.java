package com.benchos.api.shared.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Lightweight HTTPS email sender for Resend (https://resend.com/docs/api-reference/emails/send-email).
 * Uses the standard JDK HttpClient to keep memory footprint minimal in 1 GB containers.
 */
public class ResendEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(ResendEmailSender.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final MailProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public ResendEmailSender(MailProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build();
    }

    @Override
    public void send(EmailMessage message) {
        try {
            Map<String, Object> payload = Map.of(
                    "from", properties.from(),
                    "to", List.of(message.to()),
                    "subject", message.subject(),
                    "html", message.htmlBody(),
                    "text", message.textBody()
            );

            String baseUrl = properties.resendBaseUrl().endsWith("/")
                    ? properties.resendBaseUrl().substring(0, properties.resendBaseUrl().length() - 1)
                    : properties.resendBaseUrl();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/emails"))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Authorization", "Bearer " + properties.resendApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error(
                        "Resend API rejected email to={} status={} body={}",
                        message.to(),
                        response.statusCode(),
                        response.body()
                );
                throw new IllegalStateException("Email provider returned status " + response.statusCode());
            }

            log.info("Email dispatched via Resend to={} subject={}", message.to(), message.subject());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while sending email via Resend to={}", message.to(), ex);
            throw new IllegalStateException("Interrupted while sending email", ex);
        } catch (Exception ex) {
            if (ex instanceof IllegalStateException ise) {
                throw ise;
            }
            log.error("Failed to send email via Resend to={}", message.to(), ex);
            throw new IllegalStateException("Failed to send email", ex);
        }
    }
}
