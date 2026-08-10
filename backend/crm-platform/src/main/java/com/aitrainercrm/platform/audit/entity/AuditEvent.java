package com.aitrainercrm.platform.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

/**
 * One row per sensitive action platform-wide: who (userId, may be null for
 * unauthenticated events like a failed login), what (action, resourceType,
 * resourceId), and enough detail (a small JSON blob, not a full before/after
 * diff yet - that lands with the CRM entities in later phases) to answer
 * "what happened here." Append-only: nothing in this module ever updates
 * or deletes a row, only inserts.
 */
@Entity
@Table(name = "audit_events")
@Getter
@Setter
@NoArgsConstructor
public class AuditEvent {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(name = "resource_type", length = 100)
    private String resourceType;

    @Column(name = "resource_id", length = 100)
    private String resourceId;

    @Column(length = 2000)
    private String detail;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(nullable = false)
    private Instant timestamp;

    public AuditEvent(
            UUID userId, UUID organizationId, String action, String resourceType, String resourceId,
            String detail, String ipAddress) {
        this.userId = userId;
        this.organizationId = organizationId;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.detail = detail;
        this.ipAddress = ipAddress;
        this.timestamp = Instant.now();
    }
}
