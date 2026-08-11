package com.aitrainercrm.platform.workflow.service;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.role.entity.Permission;
import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.user.repository.UserRepository;
import com.aitrainercrm.platform.workflow.dto.CreateWorkflowRequest;
import com.aitrainercrm.platform.workflow.dto.UpdateWorkflowRequest;
import com.aitrainercrm.platform.workflow.entity.Workflow;
import com.aitrainercrm.platform.workflow.entity.WorkflowRun;
import com.aitrainercrm.platform.workflow.repository.WorkflowRepository;
import com.aitrainercrm.platform.workflow.repository.WorkflowRunRepository;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD plus the two MANAGE-gated operations (toggling {@code active},
 * manually firing a run) for {@link Workflow}. Owner-scoped exactly like
 * {@code ContactService}/{@code LeadService} - see the entity's javadoc for
 * why WORKFLOW gets an owner unlike this session's other two modules.
 */
@Service
@RequiredArgsConstructor
public class WorkflowService {

    private static final Permission.Resource RESOURCE = Permission.Resource.WORKFLOW;

    private final WorkflowRepository workflowRepository;
    private final WorkflowRunRepository workflowRunRepository;
    private final UserRepository userRepository;
    private final ScopeAuthorizationService scopeAuthorizationService;
    private final WorkflowExecutionService workflowExecutionService;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<Workflow> list(UserPrincipal principal, Pageable pageable) {
        Optional<Set<UUID>> visibleOwnerIds = scopeAuthorizationService.visibleOwnerIds(principal, RESOURCE, Permission.Action.READ);
        return visibleOwnerIds
                .map(ownerIds -> workflowRepository.findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(principal.getOrganizationId(), ownerIds, pageable))
                .orElseGet(() -> workflowRepository.findByOrganizationIdAndDeletedAtIsNull(principal.getOrganizationId(), pageable));
    }

    @Transactional(readOnly = true)
    public Workflow get(UserPrincipal principal, UUID workflowId) {
        Workflow workflow = findOrThrow(principal.getOrganizationId(), workflowId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.READ, workflow.getOwnerId());
        return workflow;
    }

    @Transactional
    public Workflow create(UserPrincipal principal, CreateWorkflowRequest request) {
        UUID ownerId = resolveOwner(principal, Permission.Action.CREATE, request.ownerId());
        assertUserInOrganization(principal.getOrganizationId(), request.taskAssigneeUserId());

        Workflow workflow = new Workflow(
                principal.getOrganizationId(), ownerId, request.name(), request.triggerResource(), request.triggerEvent(), request.taskSubject());
        workflow.setDescription(request.description());
        workflow.setTaskAssigneeUserId(request.taskAssigneeUserId());
        workflowRepository.save(workflow);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "Workflow", workflow.getId()));
        return workflow;
    }

    @Transactional
    public Workflow update(UserPrincipal principal, UUID workflowId, UpdateWorkflowRequest request) {
        Workflow workflow = findOrThrow(principal.getOrganizationId(), workflowId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, workflow.getOwnerId());
        assertUserInOrganization(principal.getOrganizationId(), request.taskAssigneeUserId());

        workflow.setName(request.name());
        workflow.setDescription(request.description());
        workflow.setTaskSubject(request.taskSubject());
        workflow.setTaskAssigneeUserId(request.taskAssigneeUserId());
        workflowRepository.save(workflow);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "Workflow", workflow.getId()));
        return workflow;
    }

    @Transactional
    public Workflow setActive(UserPrincipal principal, UUID workflowId, boolean active) {
        Workflow workflow = findOrThrow(principal.getOrganizationId(), workflowId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.MANAGE, workflow.getOwnerId());

        workflow.setActive(active);
        workflowRepository.save(workflow);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "Workflow", workflow.getId()));
        return workflow;
    }

    /** Fires {@code workflow} against {@code resourceId} synchronously, as a manual test - unlike {@code WorkflowEngineListener}'s real-event path, this runs even against an inactive workflow (that's the point: verifying a draft before flipping it on). */
    @Transactional
    public Workflow runManually(UserPrincipal principal, UUID workflowId, UUID resourceId) {
        Workflow workflow = findOrThrow(principal.getOrganizationId(), workflowId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.MANAGE, workflow.getOwnerId());

        workflowExecutionService.execute(workflow, resourceId);
        return findOrThrow(principal.getOrganizationId(), workflowId);
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID workflowId) {
        Workflow workflow = findOrThrow(principal.getOrganizationId(), workflowId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.DELETE, workflow.getOwnerId());

        workflow.setDeletedAt(Instant.now());
        workflowRepository.save(workflow);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "Workflow", workflowId));
    }

    @Transactional(readOnly = true)
    public Page<WorkflowRun> listRuns(UserPrincipal principal, UUID workflowId, Pageable pageable) {
        Workflow workflow = findOrThrow(principal.getOrganizationId(), workflowId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.READ, workflow.getOwnerId());
        return workflowRunRepository.findByWorkflowIdOrderByCreatedAtDesc(workflowId, pageable);
    }

    private Workflow findOrThrow(UUID organizationId, UUID workflowId) {
        return workflowRepository
                .findByIdAndOrganizationIdAndDeletedAtIsNull(workflowId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow", workflowId));
    }

    private UUID resolveOwner(UserPrincipal principal, Permission.Action action, UUID requestedOwnerId) {
        if (requestedOwnerId == null || requestedOwnerId.equals(principal.getId())) {
            return principal.getId();
        }
        if (scopeAuthorizationService.highestGranted(principal, RESOURCE, action) != ScopeAuthorizationService.Access.ORGANIZATION) {
            throw new ForbiddenException("You can only " + action.name().toLowerCase(Locale.ROOT) + " workflows owned by yourself");
        }
        assertUserInOrganization(principal.getOrganizationId(), requestedOwnerId);
        return requestedOwnerId;
    }

    private void assertUserInOrganization(UUID organizationId, UUID userId) {
        if (userId == null) return;
        boolean exists = userRepository.findActiveById(userId).map(u -> organizationId.equals(u.getOrganizationId())).orElse(false);
        if (!exists) {
            throw new ResourceNotFoundException("User", userId);
        }
    }
}
