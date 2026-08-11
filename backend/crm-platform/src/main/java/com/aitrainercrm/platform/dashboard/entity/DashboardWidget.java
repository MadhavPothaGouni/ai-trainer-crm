package com.aitrainercrm.platform.dashboard.entity;

import com.aitrainercrm.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One tile on a {@link Dashboard} - stores no data of its own, just which
 * of {@code ReportService}'s three aggregate queries to run
 * ({@link ReportType}) plus grid layout ({@link #displayOrder}/
 * {@link #width}/{@link #height}, a 12-column-grid convention like most
 * dashboard builders use) and an optional {@link #title} override (falls
 * back to a sensible default per {@link ReportType} in the DTO layer if
 * unset).
 */
@Entity
@Table(name = "dashboard_widgets")
@Getter
@Setter
@NoArgsConstructor
public class DashboardWidget extends BaseEntity {

    /** Mirrors the three read methods on {@code ReportService} one-for-one - adding a fourth report later is a new enum constant plus a new branch in {@code DashboardService#widgetData}, not a schema change. */
    public enum ReportType {
        PIPELINE_BY_STAGE, LEAD_FUNNEL, LEADERBOARD
    }

    @Column(name = "dashboard_id", nullable = false)
    private UUID dashboardId;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, length = 30)
    private ReportType reportType;

    @Column(length = 200)
    private String title;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Column(nullable = false)
    private int width = 6;

    @Column(nullable = false)
    private int height = 4;

    public DashboardWidget(UUID dashboardId, ReportType reportType) {
        this.dashboardId = dashboardId;
        this.reportType = reportType;
    }
}
