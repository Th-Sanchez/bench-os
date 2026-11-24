package com.benchos.api.shared.push;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties(PushProperties.class)
public class PushConfig {

    private static final Logger log = LoggerFactory.getLogger(PushConfig.class);

    @Bean
    PushNotificationSender pushNotificationSender(PushProperties properties, ObjectMapper objectMapper) {
        if ("expo".equalsIgnoreCase(properties.provider())) {
            log.info("Configured ExpoPushNotificationSender (endpoint={})", properties.expoEndpointUrl());
            return new ExpoPushNotificationSender(properties, objectMapper);
        }

        log.info("Configured LoggingPushNotificationSender (provider={})", properties.provider());
        return new LoggingPushNotificationSender();
    }

    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(name = "benchos.push.daily-alerts-enabled", havingValue = "true")
    static class PushSchedulingConfiguration {
    }
}
