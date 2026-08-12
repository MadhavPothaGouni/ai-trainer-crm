package com.aitrainercrm.platform.dedupe.service;

import com.aitrainercrm.platform.account.entity.Account;
import com.aitrainercrm.platform.account.repository.AccountRepository;
import com.aitrainercrm.platform.activity.entity.Activity;
import com.aitrainercrm.platform.activity.repository.ActivityRepository;
import com.aitrainercrm.platform.attachment.entity.Attachment;
import com.aitrainercrm.platform.attachment.repository.AttachmentRepository;
import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.calendar.entity.CalendarEvent;
import com.aitrainercrm.platform.calendar.repository.CalendarEventRepository;
import com.aitrainercrm.platform.common.exception.BusinessException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.contact.entity.Contact;
import com.aitrainercrm.platform.contact.repository.ContactRepository;
import com.aitrainercrm.platform.dedupe.dto.DuplicateMatchDto;
import com.aitrainercrm.platform.dedupe.entity.DuplicateMatch;
import com.aitrainercrm.platform.dedupe.repository.DuplicateMatchRepository;
import com.aitrainercrm.platform.email.entity.EmailMessage;
import com.aitrainercrm.platform.email.repository.EmailMessageRepository;
import com.aitrainercrm.platform.lead.entity.Lead;
import com.aitrainercrm.platform.lead.repository.LeadRepository;
import com.aitrainercrm.platform.opportunity.repository.OpportunityRepository;
import com.aitrainercrm.platform.role.entity.Permission;
import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reviews and resolves {@link DuplicateMatch} rows created by {@code DuplicateDetectionListener}.
 * No {@code @PreAuthorize} anywhere in {@code DuplicateMatchController} - every method here reuses
 * LEAD:READ/UPDATE, CONTACT:READ/UPDATE, or ACCOUNT:READ/UPDATE (whichever matches the match's
 * {@link DuplicateMatch.EntityType}), checked via {@link ScopeAuthorizationService}, the same
 * "no controller-level gate, checked inline" shape {@code SlaEvaluationService#getForTicket} uses
 * for TICKET:READ. The twist: {@link #merge}/{@link #dismiss} check UPDATE against BOTH records a
 * match pairs, not just one - the first place in this codebase two independent
 * {@link ScopeAuthorizationService#assertCanAccess} calls gate a single write. See V23's migration
 * comment for why a dedicated DUPLICATE_MATCH permission would be a real security gap, not a
 * simplification: it would let someone merge two Leads they can't otherwise touch at all.
 */
@Service
@RequiredArgsConstructor
public class DuplicateMatchService {

    private final DuplicateMatchRepository duplicateMatchRepository;
    private final LeadRepository leadRepository;
    private final ContactRepository contactRepository;
    private final AccountRepository accountRepository;
    private final ActivityRepository activityRepository;
    private final AttachmentRepository attachmentRepository;
    private final EmailMessageRepository emailMessageRepository;
    private final CalendarEventRepository calendarEventRepository;
    private final OpportunityRepository opportunityRepository;
    private final ScopeAuthorizationService scopeAuthorizationService;
    private final ApplicationEventPublisher events;

    /** entityType is required, not optional - see DuplicateMatchController's javadoc for why a single required entityType is what lets this whole module skip @PreAuthorize entirely. */
    @Transactional(readOnly = true)
    public List<DuplicateMatchDto> list(UserPrincipal principal, DuplicateMatch.EntityType entityType, DuplicateMatch.Status status) {
        Optional<Set<UUID>> visibleOwnerIds =
                scopeAuthorizationService.visibleOwnerIds(principal, resourceFor(entityType), Permission.Action.READ);
        List<DuplicateMatch> matches = duplicateMatchRepository
                .findByOrganizationIdAndEntityTypeAndStatusOrderByCreatedAtAsc(principal.getOrganizationId(), entityType, status);

        return matches.stream()
                .filter(match -> bothOwnersVisible(match, principal.getOrganizationId(), visibleOwnerIds))
                .map(DuplicateMatchDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public DuplicateMatchDto get(UserPrincipal principal, UUID matchId) {
        DuplicateMatch match = findOrThrow(principal.getOrganizationId(), matchId);
        assertCanAccessBoth(principal, match, Permission.Action.READ);
        return DuplicateMatchDto.from(match);
    }

    @Transactional
    public DuplicateMatchDto merge(UserPrincipal principal, UUID matchId, UUID survivorId) {
        DuplicateMatch match = findOrThrow(principal.getOrganizationId(), matchId);
        assertPending(match);
        UUID absorbedId = absorbedIdFor(match, survivorId);
        assertCanAccessBoth(principal, match, Permission.Action.UPDATE);

        UUID organizationId = principal.getOrganizationId();
        switch (match.getEntityType()) {
            case LEAD -> mergeLead(organizationId, survivorId, absorbedId);
            case CONTACT -> mergeContact(organizationId, survivorId, absorbedId);
            case ACCOUNT -> mergeAccount(organizationId, survivorId, absorbedId);
        }

        match.resolveMerged(survivorId, absorbedId, principal.getId());
        duplicateMatchRepository.save(match);

        events.publishEvent(
                new CrmAuditEvents.RecordDeleted(principal.getId(), organizationId, resourceNameFor(match.getEntityType()), absorbedId));
        return DuplicateMatchDto.from(match);
    }

    @Transactional
    public DuplicateMatchDto dismiss(UserPrincipal principal, UUID matchId) {
        DuplicateMatch match = findOrThrow(principal.getOrganizationId(), matchId);
        assertPending(match);
        assertCanAccessBoth(principal, match, Permission.Action.UPDATE);

        match.resolveDismissed(principal.getId());
        duplicateMatchRepository.save(match);
        return DuplicateMatchDto.from(match);
    }

    private void mergeLead(UUID organizationId, UUID survivorId, UUID absorbedId) {
        Lead survivor = leadRepository.findActiveByIdAndOrganizationId(survivorId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Lead", survivorId));
        Lead absorbed = leadRepository.findActiveByIdAndOrganizationId(absorbedId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Lead", absorbedId));
        if (survivor.getStatus() == Lead.Status.CONVERTED || absorbed.getStatus() == Lead.Status.CONVERTED) {
            throw new BusinessException(
                    "DUPLICATE_MATCH_LEAD_CONVERTED", "Cannot merge a lead that has already converted", HttpStatus.CONFLICT);
        }

        reassignGenericRelatedTo(organizationId, DuplicateMatch.EntityType.LEAD, absorbedId, survivorId);
        absorbed.setDeletedAt(Instant.now());
        leadRepository.save(absorbed);
    }

    private void mergeContact(UUID organizationId, UUID survivorId, UUID absorbedId) {
        contactRepository.findActiveByIdAndOrganizationId(survivorId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Contact", survivorId));
        Contact absorbed = contactRepository.findActiveByIdAndOrganizationId(absorbedId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Contact", absorbedId));

        reassignGenericRelatedTo(organizationId, DuplicateMatch.EntityType.CONTACT, absorbedId, survivorId);
        opportunityRepository.reassignPrimaryContactId(organizationId, absorbedId, survivorId);
        absorbed.setDeletedAt(Instant.now());
        contactRepository.save(absorbed);
    }

    private void mergeAccount(UUID organizationId, UUID survivorId, UUID absorbedId) {
        accountRepository.findActiveByIdAndOrganizationId(survivorId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", survivorId));
        Account absorbed = accountRepository.findActiveByIdAndOrganizationId(absorbedId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", absorbedId));

        reassignGenericRelatedTo(organizationId, DuplicateMatch.EntityType.ACCOUNT, absorbedId, survivorId);
        contactRepository.reassignAccountId(organizationId, absorbedId, survivorId);
        opportunityRepository.reassignAccountId(organizationId, absorbedId, survivorId);
        absorbed.setDeletedAt(Instant.now());
        accountRepository.save(absorbed);
    }

    /** The four generic relatedTo tables every merge fans out to, regardless of entity type - see ActivityRepository#reassignRelatedTo's javadoc. Account/Contact/Lead merges additionally reassign their own type-specific FKs (see mergeAccount/mergeContact). */
    private void reassignGenericRelatedTo(UUID organizationId, DuplicateMatch.EntityType entityType, UUID absorbedId, UUID survivorId) {
        activityRepository.reassignRelatedTo(organizationId, Activity.RelatedToType.valueOf(entityType.name()), absorbedId, survivorId);
        attachmentRepository.reassignRelatedTo(organizationId, Attachment.RelatedToType.valueOf(entityType.name()), absorbedId, survivorId);
        emailMessageRepository.reassignRelatedTo(organizationId, EmailMessage.RelatedToType.valueOf(entityType.name()), absorbedId, survivorId);
        calendarEventRepository.reassignRelatedTo(organizationId, CalendarEvent.RelatedToType.valueOf(entityType.name()), absorbedId, survivorId);
    }

    private UUID absorbedIdFor(DuplicateMatch match, UUID survivorId) {
        if (survivorId.equals(match.getRecordAId())) return match.getRecordBId();
        if (survivorId.equals(match.getRecordBId())) return match.getRecordAId();
        throw new BusinessException(
                "DUPLICATE_MATCH_INVALID_SURVIVOR", "survivorId must be one of this match's two records", HttpStatus.BAD_REQUEST);
    }

    private void assertPending(DuplicateMatch match) {
        if (match.getStatus() != DuplicateMatch.Status.PENDING) {
            throw new BusinessException(
                    "DUPLICATE_MATCH_ALREADY_RESOLVED",
                    "This match has already been " + match.getStatus().name().toLowerCase(Locale.ROOT),
                    HttpStatus.CONFLICT);
        }
    }

    private void assertCanAccessBoth(UserPrincipal principal, DuplicateMatch match, Permission.Action action) {
        Permission.Resource resource = resourceFor(match.getEntityType());
        UUID ownerA = requireOwnerId(match.getEntityType(), match.getRecordAId(), principal.getOrganizationId());
        UUID ownerB = requireOwnerId(match.getEntityType(), match.getRecordBId(), principal.getOrganizationId());
        scopeAuthorizationService.assertCanAccess(principal, resource, action, ownerA);
        scopeAuthorizationService.assertCanAccess(principal, resource, action, ownerB);
    }

    private UUID requireOwnerId(DuplicateMatch.EntityType entityType, UUID recordId, UUID organizationId) {
        UUID ownerId = ownerIdOf(entityType, recordId, organizationId);
        if (ownerId == null) {
            throw new ResourceNotFoundException(resourceNameFor(entityType), recordId);
        }
        return ownerId;
    }

    private boolean bothOwnersVisible(DuplicateMatch match, UUID organizationId, Optional<Set<UUID>> visibleOwnerIds) {
        if (visibleOwnerIds.isEmpty()) return true; // ORGANIZATION scope - no filter, see visibleOwnerIds' own javadoc
        UUID ownerA = ownerIdOf(match.getEntityType(), match.getRecordAId(), organizationId);
        UUID ownerB = ownerIdOf(match.getEntityType(), match.getRecordBId(), organizationId);
        if (ownerA == null || ownerB == null) return false;
        return visibleOwnerIds.get().contains(ownerA) && visibleOwnerIds.get().contains(ownerB);
    }

    private UUID ownerIdOf(DuplicateMatch.EntityType entityType, UUID recordId, UUID organizationId) {
        return switch (entityType) {
            case LEAD -> leadRepository.findActiveByIdAndOrganizationId(recordId, organizationId).map(Lead::getOwnerId).orElse(null);
            case CONTACT -> contactRepository.findActiveByIdAndOrganizationId(recordId, organizationId).map(Contact::getOwnerId).orElse(null);
            case ACCOUNT -> accountRepository.findActiveByIdAndOrganizationId(recordId, organizationId).map(Account::getOwnerId).orElse(null);
        };
    }

    private Permission.Resource resourceFor(DuplicateMatch.EntityType entityType) {
        return switch (entityType) {
            case LEAD -> Permission.Resource.LEAD;
            case CONTACT -> Permission.Resource.CONTACT;
            case ACCOUNT -> Permission.Resource.ACCOUNT;
        };
    }

    private String resourceNameFor(DuplicateMatch.EntityType entityType) {
        return switch (entityType) {
            case LEAD -> "Lead";
            case CONTACT -> "Contact";
            case ACCOUNT -> "Account";
        };
    }

    private DuplicateMatch findOrThrow(UUID organizationId, UUID matchId) {
        return duplicateMatchRepository.findByIdAndOrganizationId(matchId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("DuplicateMatch", matchId));
    }
}
