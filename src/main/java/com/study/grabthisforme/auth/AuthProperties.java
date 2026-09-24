package com.study.grabthisforme.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "grabthisforme.auth")
public record AuthProperties(
    long accessTokenExpireSeconds,
    long refreshTokenExpireSeconds,
    long sessionAbsoluteExpireDays,
    long revokedRetentionDays
) {
    public AuthProperties {
        if (accessTokenExpireSeconds <= 0 || refreshTokenExpireSeconds <= 0
            || sessionAbsoluteExpireDays <= 0 || revokedRetentionDays <= 0) {
            throw new IllegalArgumentException("Authentication expiry settings must be positive");
        }
    }
}
