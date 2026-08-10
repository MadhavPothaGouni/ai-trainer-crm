package com.aitrainercrm.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "crm.security")
public record SecurityProperties(
        int maxFailedLoginAttempts,
        int accountLockoutMinutes,
        int passwordResetTokenExpirationMinutes,
        int emailVerificationTokenExpirationHours) {
}
