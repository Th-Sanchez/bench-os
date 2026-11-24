package com.benchos.api.shared.push;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "benchos.push")
public record PushProperties(
        String provider,
        String expoEndpointUrl,
        String expoAccessToken,
        boolean dailyAlertsEnabled,
        String dailyAlertsCron,
        String dailyAlertsZone
) {
    public PushProperties {
        if (provider == null || provider.isBlank()) {
            provider = "log";
        }
        if (expoEndpointUrl == null || expoEndpointUrl.isBlank()) {
            expoEndpointUrl = "https://exp.host/--/api/v2/push/send";
        }
        if (dailyAlertsCron == null || dailyAlertsCron.isBlank()) {
            dailyAlertsCron = "0 30 8 * * *";
        }
        if (dailyAlertsZone == null || dailyAlertsZone.isBlank()) {
            dailyAlertsZone = "America/Sao_Paulo";
        }
    }
}
