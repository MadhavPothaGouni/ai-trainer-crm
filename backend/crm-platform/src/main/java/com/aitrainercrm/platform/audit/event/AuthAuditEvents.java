package com.aitrainercrm.platform.audit.event;

import java.util.UUID;

/**
 * Auth-module events published via {@link org.springframework.context.ApplicationEventPublisher}
 * and consumed by {@link com.aitrainercrm.platform.audit.listener.AuditEventListener}, which is
 * the only thing that writes to the audit_events table. This is the
 * pattern every module in this platform uses to feed the audit trail:
 * publish a domain event, let the audit module (which nothing else
 * depends on) record it - the auth module has no idea an audit log exists.
 */
public final class AuthAuditEvents {

    private AuthAuditEvents() {
    }

    public record UserRegistered(UUID userId, String email, UUID organizationId, String ipAddress) {
    }

    public record UserLoggedIn(UUID userId, String email, String ipAddress, String deviceInfo) {
    }

    public record LoginFailed(String email, String ipAddress, String reason) {
    }

    public record AccountLocked(UUID userId, String email, String ipAddress) {
    }

    public record PasswordChanged(UUID userId, String email) {
    }

    public record PasswordResetRequested(UUID userId, String email, String ipAddress) {
    }

    public record UserLoggedOut(UUID userId, String email) {
    }

    public record RefreshTokenReused(UUID userId, String ipAddress) {
    }
}
