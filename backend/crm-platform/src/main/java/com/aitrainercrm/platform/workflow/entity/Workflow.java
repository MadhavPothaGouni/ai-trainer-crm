package com.aitrainercrm.platform.workflow.entity;

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
 * A personally-owned automation rule: when {@link #triggerResource} fires
 * {@link #triggerEvent} (matched against {@code CrmAuditEvents.RecordCreated}/
 * {@code RecordUpdated}/{@code RecordDeleted} by {@code WorkflowEngineListener}),
 * run {@link #actionType}. Unlike Campaign/KnowledgeArticle/CustomField/
 * CustomObject (this session's other two modules, both shared-org
 * resources with no owner), WORKFLOW was seeded in V2 at OWN/TEAM/
 * ORGANIZATION scope - a workflow belongs to whoever created it, the same
 * shape as Account/Contact/Lead/Opportunity - so this entity gets a real
 * {@link #ownerId} and {@code WorkflowService} calls
 * {@code ScopeAuthorizationService} exactly like those four do.
 *
 * <p>{@link #actionType} is an enum with exactly one member today
 * ({@code CREATE_TASK}) rather than a free-text string - deliberately
 * over-engineered for a single case, the same reasoning
 * {@code CustomField.FieldType} gets six variants instead of a string:
 * adding a second action later (e.g. {@code SEND_EMAIL}) is a new enum
 * constant and a new branch in {@code WorkflowExecutionService}, not a
 * data-shape migration.
 */
@Entity
@Table(name = "workflows")
@Getter
@Setter
@NoArgsConstructor
public class Workflow extends BaseEntity {

    /** Matches {@code Activity.RelatedToType} - the same four entities Activity can already be related to. */
    public enum TriggerResource {
        LEAD, CONTACT, ACCOUNT, OPPORTUNITY
    }

    public enum TriggerEvent {
        CREATED, UPDATED, DELETED
    }

    public enum ActionType {
        CREATE_TASK
    }

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_resource", nullable = false, length = 20)
    private TriggerResource triggerResource;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_event", nullable = false, length = 20)
    private TriggerEvent triggerEvent;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 20)
    private ActionType actionType = ActionType.CREATE_TASK;

    /** The Activity's subject when {@code actionType == CREATE_TASK}, e.g. "Follow up on new lead". */
    @Column(name = "task_subject", nullable = false, length = 200)
    private String taskSubject;

    /** Who the created task is assigned to; {@code null} means "whoever owns the record that triggered this" - see WorkflowExecutionService#resolveAssignee. */
    @Column(name = "task_assignee_user_id")
    private UUID taskAssigneeUserId;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "run_count", nullable = false)
    private int runCount = 0;

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public Workflow(
            UUID organizationId, UUID ownerId, String name, TriggerResource triggerResource, TriggerEvent triggerEvent, String taskSubject) {
        this.organizationId = organizationId;
        this.ownerId = ownerId;
        this.name = name;
        this.triggerResource = triggerResource;
        this.triggerEvent = triggerEvent;
        this.taskSubject = taskSubject;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /** Called by {@code WorkflowExecutionService} on every successful fire - stamps run telemetry directly on the definition, alongside the detailed per-fire history in {@code WorkflowRun}. */
    public void recordRun() {
        this.runCount++;
        this.lastRunAt = Instant.now();
    }
}
