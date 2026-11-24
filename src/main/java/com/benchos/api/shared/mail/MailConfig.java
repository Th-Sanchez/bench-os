package com.benchos.api.shared.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties(MailProperties.class)
public class MailConfig {

    private static final Logger log = LoggerFactory.getLogger(MailConfig.class);

    @Bean
    EmailSender emailSender(MailProperties properties, ObjectMapper objectMapper) {
        if ("resend".equalsIgnoreCase(properties.provider())) {
            if (properties.resendApiKey() == null || properties.resendApiKey().isBlank()) {
                log.warn(
                        "MAIL_PROVIDER is set to 'resend' but RESEND_API_KEY is empty. "
                                + "Falling back to LoggingEmailSender until RESEND_API_KEY is configured."
                );
                return new LoggingEmailSender();
            }
            log.info("Configured ResendEmailSender (from={})", properties.from());
            return new ResendEmailSender(properties, objectMapper);
        }

        log.info("Configured LoggingEmailSender (provider={})", properties.provider());
        return new LoggingEmailSender();
    }
}
