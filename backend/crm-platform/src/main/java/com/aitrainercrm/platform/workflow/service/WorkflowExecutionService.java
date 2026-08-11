package com.aitrainercrm.platform.workflow.service;

import com.aitrainercrm.platform.account.entity.Account;
import com.aitrainercrm.platform.account.repository.AccountRepository;
import com.aitrainercrm.platform.activity.entity.Activity;
import com.aitrainercrm.platform.activity.repository.ActivityRepository;
import com.aitrainercrm.platform.contact.entity.Contact;
import com.aitrainercrm.platform.contact.repository.ContactRepository;
import com.aitrainercrm.platform.lead.entity.Lead;
import com.aitrainercrm.platform.lead.repository.LeadRepository;
import com.aitrainercrm.platform.opportunity.entity.Opportunity;
import com.aitrainercrm.platform.opportunity.repository.OpportunityRepository;
import com.aitrainercrm.platform.workflow.entity.Workflow;
import com.aitrainercrm.platform.workflow.entity.WorkflowRun;
import com.aitrainercrm.platform.workflow.repository.WorkflowRepository;
import com.aitrainercrm.platform.workflow.repository.WorkflowRunRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs one {@link Workflow} against one triggering record, called either by
 * {@code WorkflowEngineListener} (a real CRM event just fired) or directly
 * by {@code WorkflowController#run} (a manual test-fire). The only
 * {@link Workflow.ActionType} today is {@code CREATE_TASK}: create an
 * {@code Activity} of type TASK, related to the same record, assigned to
 * either the workflow's configured {@code taskAssigneeUserId} or - if
 * unset - whoever currently owns the triggering record.
 *
 * <p>Deliberately never lets a failure propagate: every path ends in a
 * {@link WorkflowRun} row (SUCCEEDED or FAILED), never an exception, the
 * same fire-and-forget-but-log-it philosophy {@code WebhookDispatchListener}
 * uses for delivery failures - a broken workflow must never break the
 * Lead/Contact/Account/Opportunity write that triggered it.
 *
 * <p>Resolving the record's current owner uses each entity repository's
 * plain {@code findById} (from {@code JpaRepository}, not the
 * {@code findActiveByIdAndOrganizationId} every service method otherwise
 * uses) deliberately: on a DELETED trigger the row is soft-deleted (
 * {@code deletedAt} set) by the time this runs, and the workflow still
 * needs its owner to assign the "record was deleted" follow-up task to.
 */
@Service
@RequiredArgsConstructor
public class WorkflowExecutionService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowExecutionService.class);
    private static final int MAX_ERROR_MESSAGE_LENGTH = 500;

    private final WorkflowRepository workflowRepository;
    private final WorkflowRunRepository workflowRunRepository;
    private final ActivityRepository activityRepository;
    private final LeadRepository leadRepository;
    private final ContactRepository contactRepository;
    private final AccountRepository accountRepository;
    private final OpportunityRepository opportunityRepository;

    @Transactional
    public void execute(Workflow workflow, UUID resourceId) {
        try {
            UUID recordOwnerId = resolveRecordOwner(workflow.getTriggerResource(), workflow.getOrganizationId(), resourceId);
            UUID assigneeId = workflow.getTaskAssigneeUserId() != null ? workflow.getTaskAssigneeUserId() : recordOwnerId;
            if (assigneeId == null) {
                throw new IllegalStateException("Could not resolve a task assignee - the triggering record has no owner and no fallback assignee is configured");
            }

            Activity.RelatedToType relatedToType = Activity.RelatedToType.valueOf(workflow.getTriggerResource().name());
            Activity activity = new Activity(
                    workflow.getOrganizationId(), Activity.Type.TASK, workflow.getTaskSubject(), relatedToType, resourceId, assigneeId);
            activityRepository.save(activity);

            workflow.recordRun();
            workflowRepository.save(workflow);
            workflowRunRepository.save(WorkflowRun.succeeded(workflow.getId(), workflow.getOrganizationId(), resourceId, activity.getId()));
        } catch (Exception e) {
            log.warn("Workflow {} failed to run against {} {}: {}", workflow.getId(), workflow.getTriggerResource(), resourceId, e.toString());
            workflowRunRepository.save(WorkflowRun.failed(workflow.getId(), workflow.getOrganizationId(), resourceId, truncate(e.getMessage())));
        }
    }

    private UUID resolveRecordOwner(Workflow.TriggerResource resource, UUID organizationId, UUID resourceId) {
        return switch (resource) {
            case LEAD -> leadRepository.findById(resourceId)
                    .filter(lead -> organizationId.equals(lead.getOrganizationId()))
                    .map(Lead::getOwnerId)
                    .orElse(null);
            case CONTACT -> contactRepository.findById(resourceId)
                    .filter(contact -> organizationId.equals(contact.getOrganizationId()))
                    .map(Contact::getOwnerId)
                    .orElse(null);
            case ACCOUNT -> accountRepository.findById(resourceId)
                    .filter(account -> organizationId.equals(account.getOrganizationId()))
                    .map(Account::getOwnerId)
                    .orElse(null);
            case OPPORTUNITY -> opportunityRepository.findById(resourceId)
                    .filter(opportunity -> organizationId.equals(opportunity.getOrganizationId()))
                    .map(Opportunity::getOwnerId)
                    .orElse(null);
        };
    }

    private String truncate(String message) {
        if (message == null) return "Unknown error";
        return message.length() <= MAX_ERROR_MESSAGE_LENGTH ? message : message.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }
}
