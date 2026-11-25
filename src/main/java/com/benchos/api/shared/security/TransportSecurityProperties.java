package com.benchos.api.shared.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "benchos.security.transport")
public record TransportSecurityProperties(
        Boolean requireHttps
) {
    public TransportSecurityProperties {
        if (requireHttps == null) {
            requireHttps = Boolean.FALSE;
        }
    }

    public boolean isRequireHttps() {
        return Boolean.TRUE.equals(requireHttps);
    }
}
