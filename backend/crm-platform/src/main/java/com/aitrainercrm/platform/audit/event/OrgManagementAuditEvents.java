package com.aitrainercrm.platform.audit.event;

import java.util.UUID;

/**
 * Events for the RBAC/user-management surface (invite, role change, status
 * change, removal) - same publish-and-forget pattern as
 * {@link AuthAuditEvents}, consumed exclusively by
 * {@link com.aitrainercrm.platform.audit.listener.AuditEventListener}.
 * These matter more than most audit events in the platform: "who changed
 * whose permissions" is the first thing an admin needs to answer after
 * anything goes wrong with access control.
 */
public final class OrgManagementAuditEvents {

    private OrgManagementAuditEvents() {
    }

    public record UserInvited(UUID actorUserId, UUID invitedUserId, String invitedEmail, UUID organizationId) {
    }

    public record UserRolesChanged(UUID actorUserId, UUID targetUserId, UUID organizationId) {
    }

    public record UserStatusChanged(UUID actorUserId, UUID targetUserId, String newStatus, UUID organizationId) {
    }

    public record UserRemoved(UUID actorUserId, UUID targetUserId, UUID organizationId) {
    }
}
