package com.aitrainercrm.platform.audit.listener;

import com.aitrainercrm.platform.audit.entity.AuditEvent;
import com.aitrainercrm.platform.audit.event.AuthAuditEvents;
import com.aitrainercrm.platform.audit.repository.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * The only writer of audit_events rows. Runs {@code @Async} - a slow audit
 * write (or, worse, the audit table being briefly locked) must never make
 * the triggering request (a login, a password change) slower or fail
 * because of it. That's also why this listens to already-published domain
 * events rather than being called synchronously from AuthService: audit
 * logging is a side effect of what happened, not a precondition for it
 * succeeding.
 */
@Component
@RequiredArgsConstructor
public class AuditEventListener {

    private final AuditEventRepository auditEventRepository;

    @Async
    @EventListener
    public void onUserRegistered(AuthAuditEvents.UserRegistered event) {
        save(event.userId(), event.organizationId(), "USER_REGISTERED", "User", event.userId(), event.ipAddress());
    }

    @Async
    @EventListener
    public void onUserLoggedIn(AuthAuditEvents.UserLoggedIn event) {
        save(event.userId(), null, "USER_LOGGED_IN", "User", event.userId(), event.ipAddress());
    }

    @Async
    @EventListener
    public void onLoginFailed(AuthAuditEvents.LoginFailed event) {
        AuditEvent auditEvent = new AuditEvent(
                null, null, "LOGIN_FAILED", "User", event.email(), "reason=" + event.reason(), event.ipAddress());
        auditEventRepository.save(auditEvent);
    }

    @Async
    @EventListener
    public void onAccountLocked(AuthAuditEvents.AccountLocked event) {
        save(event.userId(), null, "ACCOUNT_LOCKED", "User", event.userId(), event.ipAddress());
    }

    @Async
    @EventListener
    public void onPasswordChanged(AuthAuditEvents.PasswordChanged event) {
        save(event.userId(), null, "PASSWORD_CHANGED", "User", event.userId(), null);
    }

    @Async
    @EventListener
    public void onPasswordResetRequested(AuthAuditEvents.PasswordResetRequested event) {
        save(event.userId(), null, "PASSWORD_RESET_REQUESTED", "User", event.userId(), event.ipAddress());
    }

    @Async
    @EventListener
    public void onUserLoggedOut(AuthAuditEvents.UserLoggedOut event) {
        save(event.userId(), null, "USER_LOGGED_OUT", "User", event.userId(), null);
    }

    @Async
    @EventListener
    public void onRefreshTokenReused(AuthAuditEvents.RefreshTokenReused event) {
        AuditEvent auditEvent = new AuditEvent(
                event.userId(), null, "REFRESH_TOKEN_REUSE_DETECTED", "User", event.userId(),
                "possible token theft - full session revoked", event.ipAddress());
        auditEventRepository.save(auditEvent);
    }

    private void save(java.util.UUID userId, java.util.UUID organizationId, String action, String resourceType, Object resourceId, String ipAddress) {
        auditEventRepository.save(new AuditEvent(
                userId, organizationId, action, resourceType, String.valueOf(resourceId), null, ipAddress));
    }
}
