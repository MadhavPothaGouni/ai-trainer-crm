package com.aitrainercrm.platform.approval.service;

import com.aitrainercrm.platform.approval.dto.ApprovalTaskDto;
import com.aitrainercrm.platform.approval.dto.CreateApprovalRequestRequest;
import com.aitrainercrm.platform.approval.entity.ApprovalRequest;
import com.aitrainercrm.platform.approval.entity.ApprovalStep;
import com.aitrainercrm.platform.approval.repository.ApprovalRequestRepository;
import com.aitrainercrm.platform.approval.repository.ApprovalStepRepository;
import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.common.exception.BusinessException;
import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.opportunity.repository.OpportunityRepository;
import com.aitrainercrm.platform.order.repository.OrderRepository;
import com.aitrainercrm.platform.quote.repository.QuoteRepository;
import com.aitrainercrm.platform.role.entity.Permission;
import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.user.repository.UserRepository;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Multi-step, named-approver sign-off chains over a Quote/Order/
 * Opportunity. See {@link ApprovalRequest}'s javadoc and V19's migration
 * comment for how this differs from Order/Invoice's built-in {@code
 * APPROVE} status transitions.
 *
 * <p>{@link #get} is the one place the module's "fifth access pattern"
 * actually shows up in code: it tries the named-approver carve-out first
 * (any step's {@code approverUserId} matching the caller grants read
 * access to the whole request, unconditionally), and only falls back to
 * the normal owner-scope ladder off {@code requestedByUserId} if that
 * fails. {@link #list} deliberately does NOT apply that same carve-out -
 * see its own comment for why "requests I submitted" and "requests I need
 * to act on" are kept as two separate reads ({@link #list} vs. {@link
 * #myApprovalTasks}) rather than one merged query.
 */
@Service
@RequiredArgsConstructor
public class ApprovalRequestService {

    private static final Permission.Resource RESOURCE = Permission.Resource.APPROVAL_REQUEST;

    private final ApprovalRequestRepository approvalRequestRepository;
    private final ApprovalStepRepository approvalStepRepository;
    private final QuoteRepository quoteRepository;
    private final OrderRepository orderRepository;
    private final OpportunityRepository opportunityRepository;
    private final UserRepository userRepository;
    private final ScopeAuthorizationService scopeAuthorizationService;
    private final ApplicationEventPublisher events;

    /**
     * "Requests I submitted" (or that scope lets me see other submitters' requests) - NOT
     * "requests I need to act on." Those are a fundamentally different list (one is owner-scoped
     * off who asked, the other is "every step where I'm named," which has nothing to do with
     * scope at all) - merging them into one paginated query would mean either running two
     * separate queries and interleaving the results anyway, or writing a single query whose
     * WHERE clause ORs together two unrelated conditions in a way Spring Data's derived-query
     * naming can't express. Two clearly-named reads ({@link #list} here,
     * {@link #myApprovalTasks} below) is simpler than a single misleadingly-named list that
     * secretly means two different things depending on the caller. See ApprovalRequestController.
     */
    @Transactional(readOnly = true)
    public Page<ApprovalRequest> list(UserPrincipal principal, Pageable pageable) {
        Optional<Set<UUID>> visibleOwnerIds = scopeAuthorizationService.visibleOwnerIds(principal, RESOURCE, Permission.Action.READ);
        UUID organizationId = principal.getOrganizationId();
        return visibleOwnerIds
                .map(ids -> approvalRequestRepository.findByOrganizationIdAndRequestedByUserIdInOrderByCreatedAtDesc(organizationId, ids, pageable))
                .orElseGet(() -> approvalRequestRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId, pageable));
    }

    @Transactional(readOnly = true)
    public ApprovalRequest get(UserPrincipal principal, UUID requestId) {
        ApprovalRequest request = findOrThrow(principal.getOrganizationId(), requestId);
        assertCanView(principal, request);
        return request;
    }

    @Transactional(readOnly = true)
    public List<ApprovalStep> getSteps(UUID requestId) {
        return approvalStepRepository.findByApprovalRequestIdOrderByStepNumberAsc(requestId);
    }

    /** The approver's inbox - every PENDING step assigned to the caller, across every request in the org, joined with just enough of each parent request's context to render a row without a second round-trip. Batches the parent-request lookup (one findAllById, not N queries) rather than fetching each request individually. */
    @Transactional(readOnly = true)
    public Page<ApprovalTaskDto> myApprovalTasks(UserPrincipal principal, Pageable pageable) {
        Page<ApprovalStep> steps = approvalStepRepository.findByOrganizationIdAndApproverUserIdAndStatusOrderByCreatedAtAsc(
                principal.getOrganizationId(), principal.getId(), ApprovalStep.Status.PENDING, pageable);
        Map<UUID, ApprovalRequest> requestsById = approvalRequestRepository
                .findAllById(steps.getContent().stream().map(ApprovalStep::getApprovalRequestId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(ApprovalRequest::getId, request -> request));
        return steps.map(step -> ApprovalTaskDto.from(step, requestsById.get(step.getApprovalRequestId())));
    }

    /** approverUserIds is ordered - see CreateApprovalRequestRequest's javadoc. Rejects a duplicated approver id and any id that isn't a real user in this org before creating anything. */
    @Transactional
    public ApprovalRequest create(UserPrincipal principal, CreateApprovalRequestRequest request) {
        UUID organizationId = principal.getOrganizationId();
        validateRelatedTo(organizationId, request.relatedToType(), request.relatedToId());

        List<UUID> approverIds = request.approverUserIds();
        if (new HashSet<>(approverIds).size() != approverIds.size()) {
            throw new BusinessException(
                    "APPROVAL_REQUEST_DUPLICATE_APPROVER", "The same approver can't be named twice in one request", HttpStatus.BAD_REQUEST);
        }
        approverIds.forEach(approverId -> assertUserInOrganization(organizationId, approverId));

        ApprovalRequest approvalRequest = new ApprovalRequest(
                organizationId, request.relatedToType(), request.relatedToId(), principal.getId(), request.title());
        approvalRequestRepository.save(approvalRequest);

        int stepNumber = 1;
        for (UUID approverId : approverIds) {
            approvalStepRepository.save(new ApprovalStep(organizationId, approvalRequest.getId(), stepNumber, approverId));
            stepNumber++;
        }

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), organizationId, "ApprovalRequest", approvalRequest.getId()));
        return approvalRequest;
    }

    @Transactional
    public ApprovalRequest approveStep(UserPrincipal principal, UUID requestId, int stepNumber, String comment) {
        return decideStep(principal, requestId, stepNumber, true, comment);
    }

    @Transactional
    public ApprovalRequest rejectStep(UserPrincipal principal, UUID requestId, int stepNumber, String comment) {
        return decideStep(principal, requestId, stepNumber, false, comment);
    }

    @Transactional
    public ApprovalRequest cancel(UserPrincipal principal, UUID requestId) {
        ApprovalRequest request = findOrThrow(principal.getOrganizationId(), requestId);
        if (!request.getRequestedByUserId().equals(principal.getId())) {
            throw new ForbiddenException("Only the requester can cancel this approval request");
        }
        if (!request.isPending()) {
            throw new BusinessException("APPROVAL_REQUEST_NOT_PENDING", "Only a pending request can be cancelled", HttpStatus.CONFLICT);
        }
        request.setStatus(ApprovalRequest.Status.CANCELLED);
        request.setDecidedAt(Instant.now());
        approvalRequestRepository.save(request);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "ApprovalRequest", requestId));
        return request;
    }

    /** No @PreAuthorize-visible scope check here at all - see this class's own javadoc for why acting on a step is gated purely on "are you its named approver," not on any OWN/TEAM/DEPARTMENT/ORGANIZATION grant. */
    private ApprovalRequest decideStep(UserPrincipal principal, UUID requestId, int stepNumber, boolean approve, String comment) {
        ApprovalRequest request = findOrThrow(principal.getOrganizationId(), requestId);
        ApprovalStep step = approvalStepRepository.findByApprovalRequestIdAndStepNumber(requestId, stepNumber)
                .orElseThrow(() -> new ResourceNotFoundException("ApprovalStep", stepNumber));

        if (!step.getApproverUserId().equals(principal.getId())) {
            throw new ForbiddenException("Only the named approver can decide on this step");
        }
        if (!request.isPending() || stepNumber != request.getCurrentStepNumber()) {
            throw new BusinessException(
                    "APPROVAL_STEP_NOT_ACTIONABLE", "This step isn't currently awaiting a decision", HttpStatus.CONFLICT);
        }

        step.setStatus(approve ? ApprovalStep.Status.APPROVED : ApprovalStep.Status.REJECTED);
        step.setComment(comment);
        step.setDecidedAt(Instant.now());
        approvalStepRepository.save(step);

        if (!approve) {
            // Rejecting any step kills the whole chain - later steps are simply never reached,
            // still sitting at PENDING (not flipped to some "SKIPPED" status - see ApprovalStep's
            // enum, which deliberately has no third value for this).
            request.setStatus(ApprovalRequest.Status.REJECTED);
            request.setDecidedAt(Instant.now());
        } else {
            int totalSteps = approvalStepRepository.findByApprovalRequestIdOrderByStepNumberAsc(requestId).size();
            if (stepNumber == totalSteps) {
                request.setStatus(ApprovalRequest.Status.APPROVED);
                request.setDecidedAt(Instant.now());
            } else {
                request.setCurrentStepNumber(stepNumber + 1);
            }
        }
        approvalRequestRepository.save(request);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "ApprovalRequest", request.getId()));
        return request;
    }

    private void assertCanView(UserPrincipal principal, ApprovalRequest request) {
        if (approvalStepRepository.existsByApprovalRequestIdAndApproverUserId(request.getId(), principal.getId())) {
            return;
        }
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.READ, request.getRequestedByUserId());
    }

    private ApprovalRequest findOrThrow(UUID organizationId, UUID requestId) {
        return approvalRequestRepository.findByIdAndOrganizationId(requestId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("ApprovalRequest", requestId));
    }

    private void assertUserInOrganization(UUID organizationId, UUID userId) {
        boolean exists = userRepository.findActiveById(userId).map(u -> organizationId.equals(u.getOrganizationId())).orElse(false);
        if (!exists) {
            throw new ResourceNotFoundException("User", userId);
        }
    }

    private void validateRelatedTo(UUID organizationId, ApprovalRequest.RelatedToType relatedToType, UUID relatedToId) {
        boolean exists = switch (relatedToType) {
            case QUOTE -> quoteRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(relatedToId, organizationId);
            case ORDER -> orderRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(relatedToId, organizationId);
            case OPPORTUNITY -> opportunityRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(relatedToId, organizationId);
        };
        if (!exists) {
            throw new ResourceNotFoundException(relatedToType.name(), relatedToId);
        }
    }
}
