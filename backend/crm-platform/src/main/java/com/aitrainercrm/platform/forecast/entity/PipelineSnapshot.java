package com.aitrainercrm.platform.forecast.entity;

import com.aitrainercrm.platform.common.entity.BaseEntity;
import com.aitrainercrm.platform.opportunity.entity.Opportunity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One (organization, snapshot_date, owner, stage) row of pipeline history, captured once a day by
 * {@code PipelineSnapshotService#captureDaily}. Entirely system-written - there is no create/
 * update/delete endpoint anywhere in {@code forecast/}, unlike every owner-scoped or admin-config
 * module elsewhere in this codebase. See V22's migration comment for why this is a genuinely
 * different concept from {@code report/}'s live, un-persisted aggregation, not a duplicate of it.
 */
@Entity
@Table(name = "pipeline_snapshots")
@Getter
@Setter
@NoArgsConstructor
public class PipelineSnapshot extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    /** The Opportunity's ownerId as of the moment this snapshot was captured - not a live reference, and never updated afterward even if the deal is later reassigned. */
    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Opportunity.Stage stage;

    @Column(name = "deal_count", nullable = false)
    private int dealCount;

    @Column(name = "total_value", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalValue;

    public PipelineSnapshot(
            UUID organizationId, LocalDate snapshotDate, UUID ownerId, Opportunity.Stage stage, int dealCount, BigDecimal totalValue) {
        this.organizationId = organizationId;
        this.snapshotDate = snapshotDate;
        this.ownerId = ownerId;
        this.stage = stage;
        this.dealCount = dealCount;
        this.totalValue = totalValue;
    }
}
