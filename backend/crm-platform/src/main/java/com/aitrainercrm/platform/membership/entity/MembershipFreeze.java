package com.aitrainercrm.platform.membership.entity;

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
 * A client pausing an active {@link Membership} for a date range - see V62's migration comment
 * for the backstory. Owner-scoped, same shape every other occurrence entity in this platform uses.
 * {@link #freezeStart}/{@link #freezeEnd} are plain dates since a freeze always spans whole days.
 * {@link #status} is a free state machine (REQUESTED/ACTIVE/ENDED) - moving an ENDED freeze back to
 * ACTIVE is a legitimate correction, same restraint every other status machine in this platform
 * documents.
 */
@Entity
@Table(name = "membership_freezes")
@Getter
@Setter
@NoArgsConstructor
public class MembershipFreeze extends BaseEntity {

    public enum Status {
        REQUESTED, ACTIVE, ENDED
    }

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "membership_id", nullable = false)
    private UUID membershipId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "freeze_start", nullable = false)
    private LocalDate freezeStart;

    @Column(name = "freeze_end", nullable = false)
    private LocalDate freezeEnd;

    @Column(length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.REQUESTED;

    @Column(length = 2000)
    private String notes;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public MembershipFreeze(UUID organizationId, UUID membershipId, UUID ownerId, LocalDate freezeStart, LocalDate freezeEnd) {
        this.organizationId = organizationId;
        this.membershipId = membershipId;
        this.ownerId = ownerId;
        this.freezeStart = freezeStart;
        this.freezeEnd = freezeEnd;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
