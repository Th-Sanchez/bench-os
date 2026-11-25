package com.benchos.api.shared.ratelimit;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig {

    @Bean
    RateLimiter rateLimiter(RateLimitProperties properties) {
        return new InMemoryRateLimiter(Clock.systemUTC(), properties.maxEntries());
    }
}
