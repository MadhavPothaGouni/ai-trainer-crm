package com.aitrainercrm.platform.importexport.entity;

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
 * One row per CSV upload processed by {@code ImportExportService#importCsv}. A run log, not a
 * soft-deletable business record - see V13's migration comment.
 *
 * <p>{@link Status#COMPLETED} means the job ran to completion, which is <em>not</em> the same as
 * "every row succeeded" - a job where all 40 rows failed validation is still COMPLETED with
 * {@code successCount = 0}, because the import itself did what it was asked (process every row
 * and report what happened to each one). {@link Status#FAILED} is reserved for a job that never
 * got that far at all - an empty upload, a file that isn't valid CSV, or a header row missing a
 * required column - see {@code ImportExportService#parseAndValidateHeader}.
 */
@Entity
@Table(name = "import_jobs")
@Getter
@Setter
@NoArgsConstructor
public class ImportJob extends BaseEntity {

    public enum EntityType {
        ACCOUNT, CONTACT, LEAD, TICKET
    }

    public enum Status {
        COMPLETED, FAILED
    }

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 20)
    private EntityType entityType;

    @Column(name = "initiated_by_user_id", nullable = false)
    private UUID initiatedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    @Column(name = "success_count", nullable = false)
    private int successCount;

    @Column(name = "error_count", nullable = false)
    private int errorCount;

    public ImportJob(UUID organizationId, EntityType entityType, UUID initiatedByUserId) {
        this.organizationId = organizationId;
        this.entityType = entityType;
        this.initiatedByUserId = initiatedByUserId;
        this.status = Status.COMPLETED;
    }

    public static ImportJob failed(UUID organizationId, EntityType entityType, UUID initiatedByUserId) {
        ImportJob job = new ImportJob(organizationId, entityType, initiatedByUserId);
        job.status = Status.FAILED;
        return job;
    }
}
