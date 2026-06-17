package com.study.grabthisforme.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "grabthisforme.auth")
public record AuthProperties(String tokenSecret, long tokenExpireHours) {
}
