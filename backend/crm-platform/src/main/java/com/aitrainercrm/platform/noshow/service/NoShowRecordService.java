package com.aitrainercrm.platform.noshow.service;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.common.exception.BusinessException;
import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.contact.repository.ContactRepository;
import com.aitrainercrm.platform.noshow.dto.CreateNoShowRecordRequest;
import com.aitrainercrm.platform.noshow.dto.UpdateNoShowRecordRequest;
import com.aitrainercrm.platform.noshow.entity.NoShowRecord;
import com.aitrainercrm.platform.noshow.repository.NoShowRecordRepository;
import com.aitrainercrm.platform.role.entity.Permission;
import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.user.repository.UserRepository;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A client missing a scheduled booking - see {@link NoShowRecord}'s javadoc and V58's migration
 * comment for the backstory. Follows the same OWN/TEAM/DEPARTMENT/ORGANIZATION record-level
 * authorization shape as {@code ReferralService}, with {@code resolveOwner} defaulting a null
 * {@code ownerId} to the caller. {@link #waive} is the one piece of real business logic - a
 * dedicated, business-rule-checked action rather than a free status field, mirroring
 * {@code ReferralService#issueReward}'s and {@code GiftCardService#redeem}'s shape.
 */
@Service
@RequiredArgsConstructor
public class NoShowRecordService {

    private static final Permission.Resource RESOURCE = Permission.Resource.NO_SHOW_RECORD;

    private final NoShowRecordRepository noShowRecordRepository;
    private final ContactRepository contactRepository;
    private final UserRepository userRepository;
    private final ScopeAuthorizationService scopeAuthorizationService;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<NoShowRecord> list(UserPrincipal principal, Pageable pageable) {
        Optional<Set<UUID>> visibleOwnerIds = scopeAuthorizationService.visibleOwnerIds(principal, RESOURCE, Permission.Action.READ);
        return visibleOwnerIds
                .map(ownerIds -> noShowRecordRepository.findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(principal.getOrganizationId(), ownerIds, pageable))
                .orElseGet(() -> noShowRecordRepository.findByOrganizationIdAndDeletedAtIsNull(principal.getOrganizationId(), pageable));
    }

    @Transactional(readOnly = true)
    public NoShowRecord get(UserPrincipal principal, UUID noShowRecordId) {
        NoShowRecord record = findOrThrow(principal.getOrganizationId(), noShowRecordId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.READ, record.getOwnerId());
        return record;
    }

    @Transactional
    public NoShowRecord create(UserPrincipal principal, CreateNoShowRecordRequest request) {
        UUID ownerId = resolveOwner(principal, Permission.Action.CREATE, request.ownerId());
        assertContactInOrganization(principal.getOrganizationId(), request.contactId());

        NoShowRecord record = new NoShowRecord(principal.getOrganizationId(), request.contactId(), ownerId, request.occurredAt());
        record.setRelatedType(request.relatedType());
        record.setFeeAmount(request.feeAmount());
        record.setNotes(request.notes());
        noShowRecordRepository.save(record);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "NoShowRecord", record.getId()));
        return record;
    }

    @Transactional
    public NoShowRecord update(UserPrincipal principal, UUID noShowRecordId, UpdateNoShowRecordRequest request) {
        NoShowRecord record = findOrThrow(principal.getOrganizationId(), noShowRecordId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, record.getOwnerId());

        record.setOccurredAt(request.occurredAt());
        record.setRelatedType(request.relatedType());
        record.setFeeAmount(request.feeAmount());
        record.setNotes(request.notes());
        noShowRecordRepository.save(record);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "NoShowRecord", record.getId()));
        return record;
    }

    /**
     * Waives the no-show fee - requires a fee to actually be set (waiving a record with no fee is
     * a data-entry mistake, not a valid action, same restraint {@code ReferralService#issueReward}
     * applies to an unset rewardAmount) and requires the record not already be waived. {@code waived}/
     * {@code waivedAt} flip exactly once; there's no un-waive action, same one-way-door shape
     * {@code GiftCardService#redeem} uses for balance deduction.
     */
    @Transactional
    public NoShowRecord waive(UserPrincipal principal, UUID noShowRecordId) {
        NoShowRecord record = findOrThrow(principal.getOrganizationId(), noShowRecordId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, record.getOwnerId());

        if (record.getFeeAmount() == null) {
            throw new BusinessException("NO_SHOW_RECORD_NO_FEE", "There's no fee on this record to waive", HttpStatus.CONFLICT);
        }
        if (record.isWaived()) {
            throw new BusinessException("NO_SHOW_RECORD_ALREADY_WAIVED", "This fee has already been waived", HttpStatus.CONFLICT);
        }
        record.setWaived(true);
        record.setWaivedAt(Instant.now());
        noShowRecordRepository.save(record);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "NoShowRecord", record.getId()));
        return record;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID noShowRecordId) {
        NoShowRecord record = findOrThrow(principal.getOrganizationId(), noShowRecordId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.DELETE, record.getOwnerId());

        record.setDeletedAt(Instant.now());
        noShowRecordRepository.save(record);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "NoShowRecord", noShowRecordId));
    }

    private NoShowRecord findOrThrow(UUID organizationId, UUID noShowRecordId) {
        return noShowRecordRepository.findActiveByIdAndOrganizationId(noShowRecordId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("NoShowRecord", noShowRecordId));
    }

    private UUID resolveOwner(UserPrincipal principal, Permission.Action action, UUID requestedOwnerId) {
        if (requestedOwnerId == null || requestedOwnerId.equals(principal.getId())) {
            return principal.getId();
        }
        if (scopeAuthorizationService.highestGranted(principal, RESOURCE, action) != ScopeAuthorizationService.Access.ORGANIZATION) {
            throw new ForbiddenException("You can only " + action.name().toLowerCase(Locale.ROOT) + " no-show records you manage");
        }
        assertUserInOrganization(principal.getOrganizationId(), requestedOwnerId);
        return requestedOwnerId;
    }

    private void assertUserInOrganization(UUID organizationId, UUID userId) {
        boolean exists = userRepository.findActiveById(userId).map(u -> organizationId.equals(u.getOrganizationId())).orElse(false);
        if (!exists) {
            throw new ResourceNotFoundException("User", userId);
        }
    }

    private void assertContactInOrganization(UUID organizationId, UUID contactId) {
        if (!contactRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(contactId, organizationId)) {
            throw new ResourceNotFoundException("Contact", contactId);
        }
    }
}
