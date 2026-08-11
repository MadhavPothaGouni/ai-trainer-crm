package com.aitrainercrm.platform.workflow.listener;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.workflow.entity.Workflow;
import com.aitrainercrm.platform.workflow.repository.WorkflowRepository;
import com.aitrainercrm.platform.workflow.service.WorkflowExecutionService;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * The workflow-automation counterpart to {@code WebhookDispatchListener} and
 * {@code AuditEventListener} - a third, entirely independent
 * {@code @EventListener} on the same {@link CrmAuditEvents} bus.
 * LeadService/ContactService/AccountService/OpportunityService have no idea
 * this listener exists; they just publish {@code RecordCreated}/
 * {@code RecordUpdated}/{@code RecordDeleted} the way they always have.
 *
 * <p>{@code @Async} for the same reason every other listener on this bus
 * is: a workflow with a broken configuration (or ten of them) must never
 * slow down or fail the request that created the record that triggered
 * them.
 *
 * <p>Only four {@code resourceType} values ever match anything -
 * {@code "Lead"}/{@code "Contact"}/{@code "Account"}/{@code "Opportunity"},
 * the same four {@code Activity.RelatedToType} covers. Events for every
 * other resource (Product, Quote, Order, Campaign, ...) are published on
 * this exact same bus but simply never match a {@link Workflow.TriggerResource},
 * so {@link #parseResource} returning {@code null} for them is the normal,
 * expected case, not an error worth logging.
 */
@Component
@RequiredArgsConstructor
public class WorkflowEngineListener {

    private final WorkflowRepository workflowRepository;
    private final WorkflowExecutionService workflowExecutionService;

    @Async
    @EventListener
    public void onRecordCreated(CrmAuditEvents.RecordCreated event) {
        dispatch(event.organizationId(), event.resourceType(), event.resourceId(), Workflow.TriggerEvent.CREATED);
    }

    @Async
    @EventListener
    public void onRecordUpdated(CrmAuditEvents.RecordUpdated event) {
        dispatch(event.organizationId(), event.resourceType(), event.resourceId(), Workflow.TriggerEvent.UPDATED);
    }

    @Async
    @EventListener
    public void onRecordDeleted(CrmAuditEvents.RecordDeleted event) {
        dispatch(event.organizationId(), event.resourceType(), event.resourceId(), Workflow.TriggerEvent.DELETED);
    }

    private void dispatch(UUID organizationId, String resourceType, UUID resourceId, Workflow.TriggerEvent triggerEvent) {
        Workflow.TriggerResource resource = parseResource(resourceType);
        if (resource == null) return;

        List<Workflow> matches = workflowRepository
                .findByOrganizationIdAndTriggerResourceAndTriggerEventAndActiveTrueAndDeletedAtIsNull(organizationId, resource, triggerEvent);
        for (Workflow workflow : matches) {
            workflowExecutionService.execute(workflow, resourceId);
        }
    }

    private Workflow.TriggerResource parseResource(String resourceType) {
        try {
            return Workflow.TriggerResource.valueOf(resourceType.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
