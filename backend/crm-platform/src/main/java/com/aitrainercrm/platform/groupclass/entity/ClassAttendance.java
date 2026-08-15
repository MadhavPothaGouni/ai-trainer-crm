package com.aitrainercrm.platform.groupclass.entity;

import com.aitrainercrm.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One {@link com.aitrainercrm.platform.contact.entity.Contact}'s registration on a
 * {@link ClassSession}'s roster - the "N clients show up to the same session" fact nothing else
 * in the schema models (see V43's migration comment). Owner-scoped like its parent session, but
 * {@link #ownerId} is *copied* from {@code classSession.ownerId} at creation rather than
 * independently resolved - whoever can manage a session can manage its roster, so there is no
 * separate "assign this attendance to a different owner" request field the way
 * Membership/ClientGoal have for their own owner. {@link #registeredAt} is stamped once at
 * creation and never changes; {@link #checkedInAt} follows the "stamp once, never overwrite"
 * rule {@code Contract#signedAt}/{@code ClientGoal#achievedAt} already established - the first
 * time status moves to ATTENDED, not the most recent time, so correcting a mistaken NO_SHOW back
 * to ATTENDED doesn't erase when the client actually walked in.
 */
@Entity
@Table(name = "class_attendances")
@Getter
@Setter
@NoArgsConstructor
public class ClassAttendance extends BaseEntity {

    public enum Status {
        REGISTERED, ATTENDED, NO_SHOW, CANCELLED
    }

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "class_session_id", nullable = false)
    private UUID classSessionId;

    @Column(name = "contact_id", nullable = false)
    private UUID contactId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.REGISTERED;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt = Instant.now();

    @Column(name = "checked_in_at")
    private Instant checkedInAt;

    @Column(length = 500)
    private String notes;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public ClassAttendance(UUID organizationId, UUID classSessionId, UUID contactId, UUID ownerId) {
        this.organizationId = organizationId;
        this.classSessionId = classSessionId;
        this.contactId = contactId;
        this.ownerId = ownerId;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
