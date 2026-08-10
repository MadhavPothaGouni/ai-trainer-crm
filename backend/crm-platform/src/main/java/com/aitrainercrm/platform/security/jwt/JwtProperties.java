package com.aitrainercrm.platform.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "crm.jwt")
public record JwtProperties(
        String secret, int accessTokenExpirationMinutes, int refreshTokenExpirationDays, String issuer) {
}
