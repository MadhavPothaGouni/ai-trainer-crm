package com.aitrainercrm.platform.workflow.entity;

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
 * One row per time a {@link Workflow} actually fired - an immutable
 * execution log, not a soft-deletable business record (no
 * {@code deletedAt}; {@link Workflow#recordRun()} tracks the running
 * totals directly on the definition, this table is the detailed per-fire
 * history behind those totals). {@code createdAt} (inherited from
 * {@link BaseEntity}) doubles as "when this run happened."
 *
 * <p>Captures failures, not just successes - see {@link Status#FAILED} and
 * {@link #errorMessage} - so an owner can see *why* a workflow didn't
 * create the task they expected (e.g. the resolved assignee no longer
 * exists) instead of the run silently vanishing.
 */
@Entity
@Table(name = "workflow_runs")
@Getter
@Setter
@NoArgsConstructor
public class WorkflowRun extends BaseEntity {

    public enum Status {
        SUCCEEDED, FAILED
    }

    @Column(name = "workflow_id", nullable = false)
    private UUID workflowId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    /** The Lead/Contact/Account/Opportunity id that triggered this run - see V11's migration comment for why this isn't a foreign key. */
    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;

    @Column(name = "created_activity_id")
    private UUID createdActivityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    public WorkflowRun(UUID workflowId, UUID organizationId, UUID resourceId) {
        this.workflowId = workflowId;
        this.organizationId = organizationId;
        this.resourceId = resourceId;
    }

    public static WorkflowRun succeeded(UUID workflowId, UUID organizationId, UUID resourceId, UUID createdActivityId) {
        WorkflowRun run = new WorkflowRun(workflowId, organizationId, resourceId);
        run.setStatus(Status.SUCCEEDED);
        run.setCreatedActivityId(createdActivityId);
        return run;
    }

    public static WorkflowRun failed(UUID workflowId, UUID organizationId, UUID resourceId, String errorMessage) {
        WorkflowRun run = new WorkflowRun(workflowId, organizationId, resourceId);
        run.setStatus(Status.FAILED);
        run.setErrorMessage(errorMessage);
        return run;
    }
}
