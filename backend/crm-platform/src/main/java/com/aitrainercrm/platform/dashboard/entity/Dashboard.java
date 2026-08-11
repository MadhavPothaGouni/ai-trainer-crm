package com.aitrainercrm.platform.dashboard.entity;

import com.aitrainercrm.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A named, owner-scoped collection of {@link DashboardWidget}s - the
 * saved-report/dashboard-builder feature {@code ReportController}'s own
 * javadoc flagged as a future pass. Owner-scoped exactly like
 * {@link com.aitrainercrm.platform.workflow.entity.Workflow} (DASHBOARD was
 * seeded in V2 alongside WORKFLOW/REPORT at OWN/TEAM/ORGANIZATION scope),
 * not a shared-org resource like this session's Campaign/CustomObject.
 *
 * <p>Stores no report data itself - {@code DashboardService#getData} pulls
 * each widget's numbers live from {@code ReportService} on every read, so
 * a saved dashboard is a saved *view*, never a stale snapshot.
 */
@Entity
@Table(name = "dashboards")
@Getter
@Setter
@NoArgsConstructor
public class Dashboard extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 2000)
    private String description;

    // Named without an "is" prefix (like every other boolean flag in this codebase - see
    // Workflow#active/CustomObject#active) so Lombok's generated getter/setter names stay
    // predictable (isDefaultDashboard()/setDefaultDashboard(...), not the inconsistent
    // isDefault()/setDefault(...) Lombok would produce from a field literally named isDefault).
    @Column(name = "is_default", nullable = false)
    private boolean defaultDashboard = false;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public Dashboard(UUID organizationId, UUID ownerId, String name) {
        this.organizationId = organizationId;
        this.ownerId = ownerId;
        this.name = name;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
