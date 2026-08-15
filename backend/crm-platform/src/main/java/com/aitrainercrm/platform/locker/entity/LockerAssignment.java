package com.aitrainercrm.platform.locker.entity;

import com.aitrainercrm.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One client's assignment to a {@link Locker} - see V50's migration comment for the gap this
 * fills. Owner-scoped like {@link com.aitrainercrm.platform.vendor.entity.PurchaseOrder}, full
 * OWN/TEAM/DEPARTMENT/ORGANIZATION ladder. {@link #contactId} is the client assigned the locker,
 * not the authorization subject - {@code ownerId} (the staff member who made the assignment) is
 * what {@link com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService} checks,
 * same split {@code ClientDocument#contactId} established. {@link #status} is a free (non-linear)
 * state machine - reactivating an EXPIRED or RETURNED assignment is a legitimate correction, never
 * blocked. {@link #returnedAt} is stamped the first time status moves to RETURNED and never
 * overwritten afterward, same "stamp once" rule {@code PurchaseOrder#receivedAt} established.
 */
@Entity
@Table(name = "locker_assignments")
@Getter
@Setter
@NoArgsConstructor
public class LockerAssignment extends BaseEntity {

    public enum Status {
        ACTIVE, RETURNED, EXPIRED
    }

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "locker_id", nullable = false)
    private UUID lockerId;

    @Column(name = "contact_id", nullable = false)
    private UUID contactId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt = Instant.now();

    @Column(name = "expires_at")
    private LocalDate expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    /** Stamped once, on entering RETURNED - see this class's javadoc. */
    @Column(name = "returned_at")
    private Instant returnedAt;

    @Column(length = 2000)
    private String notes;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public LockerAssignment(UUID organizationId, UUID lockerId, UUID contactId, UUID ownerId) {
        this.organizationId = organizationId;
        this.lockerId = lockerId;
        this.contactId = contactId;
        this.ownerId = ownerId;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
