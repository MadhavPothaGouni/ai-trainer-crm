package com.aitrainercrm.platform.audit.event;

import java.util.UUID;

/**
 * Events for the CRM domain (accounts, contacts, leads, opportunities).
 * Deliberately generic - one {@code RecordCreated}/{@code RecordUpdated}/
 * {@code RecordDeleted}/{@code RecordAssigned} record shared across all four
 * entity types, tagged with {@code resourceType} - rather than a
 * create/update/delete event per entity (16 near-identical records for no
 * real benefit; nothing here needs entity-specific fields the way
 * {@link OrgManagementAuditEvents#UserStatusChanged} needs {@code newStatus}).
 * Lead conversion is the one CRM action that genuinely doesn't fit that
 * shape (it touches up to three other records at once), so it gets its own
 * record.
 */
public final class CrmAuditEvents {

    private CrmAuditEvents() {
    }

    public record RecordCreated(UUID actorUserId, UUID organizationId, String resourceType, UUID resourceId) {
    }

    public record RecordUpdated(UUID actorUserId, UUID organizationId, String resourceType, UUID resourceId) {
    }

    public record RecordDeleted(UUID actorUserId, UUID organizationId, String resourceType, UUID resourceId) {
    }

    public record RecordAssigned(UUID actorUserId, UUID organizationId, String resourceType, UUID resourceId, UUID newOwnerId) {
    }

    public record LeadConverted(
            UUID actorUserId, UUID organizationId, UUID leadId, UUID accountId, UUID contactId, UUID opportunityId) {
    }
}
