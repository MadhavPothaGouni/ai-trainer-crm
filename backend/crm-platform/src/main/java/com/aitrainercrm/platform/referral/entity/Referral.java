package com.aitrainercrm.platform.referral.entity;

import com.aitrainercrm.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A client referring someone they know - see V46's migration comment for why Lead/Contact don't
 * already model this. Owner-scoped, same shape as {@link com.aitrainercrm.platform.clientgoal.entity.ClientGoal}:
 * {@code referrerContactId} is the existing client who made the referral (never the authorization
 * subject, same "owner and target are different people" split ClientGoal#contactId already
 * established); {@code convertedContactId} is nullable and only gets set once the referral
 * actually becomes a Contact, stamped once and never overwritten (same rule as
 * {@code achievedAt}); {@code rewardIssuedAt} is likewise stamped once, independent of
 * {@code rewardAmount} so a later correction to the amount doesn't re-trigger issuance.
 */
@Entity
@Table(name = "referrals")
@Getter
@Setter
@NoArgsConstructor
public class Referral extends BaseEntity {

    public enum Status {
        PENDING, CONTACTED, CONVERTED, DECLINED
    }

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "referrer_contact_id", nullable = false)
    private UUID referrerContactId;

    @Column(name = "referred_name", nullable = false, length = 200)
    private String referredName;

    @Column(name = "referred_email", length = 255)
    private String referredEmail;

    @Column(name = "referred_phone", length = 50)
    private String referredPhone;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    /** Stamped the first time this referral converts into an actual Contact - never overwritten afterward, same rule as {@code ClientGoal#achievedAt}. */
    @Column(name = "converted_contact_id")
    private UUID convertedContactId;

    @Column(name = "reward_amount", precision = 10, scale = 2)
    private BigDecimal rewardAmount;

    /** Stamped once, independent of rewardAmount - see this class's javadoc. */
    @Column(name = "reward_issued_at")
    private Instant rewardIssuedAt;

    @Column(length = 2000)
    private String notes;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public Referral(UUID organizationId, UUID referrerContactId, String referredName, UUID ownerId) {
        this.organizationId = organizationId;
        this.referrerContactId = referrerContactId;
        this.referredName = referredName;
        this.ownerId = ownerId;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
