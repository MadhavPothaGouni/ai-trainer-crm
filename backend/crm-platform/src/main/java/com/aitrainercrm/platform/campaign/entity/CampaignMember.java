package com.aitrainercrm.platform.campaign.entity;

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
 * One Lead or Contact's membership in a {@link Campaign}, and how they've
 * engaged with it so far. Exactly one of {@link #leadId}/{@link #contactId}
 * is ever set (enforced both by a DB check constraint in V9 and by
 * {@code CampaignService#addMember}) - a lighter version of {@code
 * Activity}'s four-way polymorphic {@code relatedTo}, since a campaign
 * member is only ever one of two types, so an actual FK per column (see
 * V9's migration) is possible here where Activity had to fall back to an
 * untyped uuid.
 *
 * <p>{@link #status} isn't a linear lifecycle the way Order/Invoice are -
 * SENT/OPENED/CLICKED/RESPONDED/CONVERTED describe an email-marketing-style
 * engagement funnel a member can be set to directly at any point (a rep
 * importing results from an external email tool might set a member straight
 * to OPENED without ever seeing a SENT event), so {@code
 * CampaignService#updateMemberStatus} doesn't validate transitions the way
 * {@code OrderService#validateStatusTransition} does. Moving to RESPONDED or
 * CONVERTED stamps {@link #respondedAt}; moving away from either clears it.
 */
@Entity
@Table(name = "campaign_members")
@Getter
@Setter
@NoArgsConstructor
public class CampaignMember extends BaseEntity {

    public enum Status {
        ADDED, SENT, OPENED, CLICKED, RESPONDED, CONVERTED
    }

    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;

    @Column(name = "lead_id")
    private UUID leadId;

    @Column(name = "contact_id")
    private UUID contactId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ADDED;

    @Column(name = "responded_at")
    private Instant respondedAt;

    public CampaignMember(UUID campaignId, UUID leadId, UUID contactId) {
        this.campaignId = campaignId;
        this.leadId = leadId;
        this.contactId = contactId;
    }
}
