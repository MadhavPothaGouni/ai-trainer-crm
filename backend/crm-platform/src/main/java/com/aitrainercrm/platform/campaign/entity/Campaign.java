package com.aitrainercrm.platform.campaign.entity;

import com.aitrainercrm.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A marketing campaign - an email blast, webinar, event, or similar - that
 * {@link CampaignMember}s (Leads or Contacts) can be added to and tracked
 * against. Like {@link com.aitrainercrm.platform.product.entity.Product}
 * and {@link com.aitrainercrm.platform.order.entity.Order}, there's no
 * {@code ownerId} - CAMPAIGN is seeded in V2 at TEAM/DEPARTMENT/
 * ORGANIZATION scope only (no OWN), so {@code CampaignService} does no
 * per-record {@code ScopeAuthorizationService} check; the controller's
 * {@code @PreAuthorize} is the whole authorization story.
 *
 * <p>{@link #status} moves PLANNED -&gt; ACTIVE -&gt; COMPLETED, with CANCELLED
 * reachable from PLANNED or ACTIVE - same shape as {@code Order.Status},
 * validated the same way ({@code CampaignService#updateStatus}). Unlike
 * Order/Invoice, the catalog didn't seed an APPROVE action for CAMPAIGN, so
 * every transition here (including the initial one) is gated on the
 * ordinary {@code CAMPAIGN:UPDATE} - there's no "sign off on it" action to
 * separate out.
 */
@Entity
@Table(name = "campaigns")
@Getter
@Setter
@NoArgsConstructor
public class Campaign extends BaseEntity {

    public enum Type {
        EMAIL, WEBINAR, EVENT, SOCIAL_MEDIA, DIRECT_MAIL, OTHER
    }

    public enum Status {
        PLANNED, ACTIVE, COMPLETED, CANCELLED
    }

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Type type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PLANNED;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(precision = 14, scale = 2)
    private BigDecimal budget;

    @Column(name = "actual_cost", precision = 14, scale = 2)
    private BigDecimal actualCost;

    @Column(length = 2000)
    private String description;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public Campaign(UUID organizationId, String name, Type type) {
        this.organizationId = organizationId;
        this.name = name;
        this.type = type;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
