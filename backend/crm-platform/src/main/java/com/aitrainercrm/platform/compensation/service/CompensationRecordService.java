package com.aitrainercrm.platform.compensation.service;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.common.exception.BusinessException;
import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.compensation.dto.CreateCompensationRecordRequest;
import com.aitrainercrm.platform.compensation.dto.UpdateCompensationRecordRequest;
import com.aitrainercrm.platform.compensation.entity.CompensationRecord;
import com.aitrainercrm.platform.compensation.repository.CompensationRecordRepository;
import com.aitrainercrm.platform.role.entity.Permission;
import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
 * One staff member's pay for one pay period - see {@link CompensationRecord}'s javadoc and V57's
 * migration comment for the backstory. Follows the same OWN/TEAM/DEPARTMENT/ORGANIZATION
 * record-level authorization shape as {@code LockerAssignmentService}, with {@code resolveOwner}
 * defaulting a null {@code ownerId} to the caller. {@link #recomputeTotal} is the one piece of
 * real business logic - {@code totalAmount} is always derived from the other four money fields,
 * never accepted directly from the client, so it can't silently drift out of sync.
 */
@Service
@RequiredArgsConstructor
public class CompensationRecordService {

    private static final Permission.Resource RESOURCE = Permission.Resource.COMPENSATION_RECORD;

    private final CompensationRecordRepository compensationRecordRepository;
    private final UserRepository userRepository;
    private final ScopeAuthorizationService scopeAuthorizationService;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<CompensationRecord> list(UserPrincipal principal, Pageable pageable) {
        Optional<Set<UUID>> visibleOwnerIds = scopeAuthorizationService.visibleOwnerIds(principal, RESOURCE, Permission.Action.READ);
        return visibleOwnerIds
                .map(ownerIds -> compensationRecordRepository.findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(principal.getOrganizationId(), ownerIds, pageable))
                .orElseGet(() -> compensationRecordRepository.findByOrganizationIdAndDeletedAtIsNull(principal.getOrganizationId(), pageable));
    }

    @Transactional(readOnly = true)
    public CompensationRecord get(UserPrincipal principal, UUID compensationRecordId) {
        CompensationRecord record = findOrThrow(principal.getOrganizationId(), compensationRecordId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.READ, record.getOwnerId());
        return record;
    }

    @Transactional
    public CompensationRecord create(UserPrincipal principal, CreateCompensationRecordRequest request) {
        UUID ownerId = resolveOwner(principal, Permission.Action.CREATE, request.ownerId());
        assertUserInOrganization(principal.getOrganizationId(), request.staffUserId());
        assertValidPeriod(request.payPeriodStart(), request.payPeriodEnd());

        CompensationRecord record = new CompensationRecord(
                principal.getOrganizationId(), request.staffUserId(), ownerId, request.payPeriodStart(), request.payPeriodEnd());
        record.setHoursWorked(request.hoursWorked());
        record.setHourlyRate(request.hourlyRate());
        record.setCommissionAmount(nullToZero(request.commissionAmount()));
        record.setBonusAmount(nullToZero(request.bonusAmount()));
        record.setNotes(request.notes());
        recomputeTotal(record);
        compensationRecordRepository.save(record);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "CompensationRecord", record.getId()));
        return record;
    }

    @Transactional
    public CompensationRecord update(UserPrincipal principal, UUID compensationRecordId, UpdateCompensationRecordRequest request) {
        CompensationRecord record = findOrThrow(principal.getOrganizationId(), compensationRecordId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, record.getOwnerId());
        assertValidPeriod(request.payPeriodStart(), request.payPeriodEnd());

        record.setPayPeriodStart(request.payPeriodStart());
        record.setPayPeriodEnd(request.payPeriodEnd());
        record.setHoursWorked(request.hoursWorked());
        record.setHourlyRate(request.hourlyRate());
        record.setCommissionAmount(nullToZero(request.commissionAmount()));
        record.setBonusAmount(nullToZero(request.bonusAmount()));
        record.setNotes(request.notes());
        recomputeTotal(record);
        compensationRecordRepository.save(record);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "CompensationRecord", record.getId()));
        return record;
    }

    /**
     * No invalid-transition checks - moving a record back to DRAFT after APPROVED is a
     * legitimate correction, same restraint every other status machine in this platform
     * documents. {@code paidAt} is stamped the first time status moves to PAID and never
     * overwritten afterward.
     */
    @Transactional
    public CompensationRecord updateStatus(UserPrincipal principal, UUID compensationRecordId, CompensationRecord.Status newStatus) {
        CompensationRecord record = findOrThrow(principal.getOrganizationId(), compensationRecordId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, record.getOwnerId());

        if (newStatus == CompensationRecord.Status.PAID && record.getPaidAt() == null) {
            record.setPaidAt(Instant.now());
        }
        record.setStatus(newStatus);
        compensationRecordRepository.save(record);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "CompensationRecord", record.getId()));
        return record;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID compensationRecordId) {
        CompensationRecord record = findOrThrow(principal.getOrganizationId(), compensationRecordId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.DELETE, record.getOwnerId());

        record.setDeletedAt(Instant.now());
        compensationRecordRepository.save(record);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "CompensationRecord", compensationRecordId));
    }

    /** totalAmount = hoursWorked * hourlyRate + commissionAmount + bonusAmount - see this class's javadoc. */
    private void recomputeTotal(CompensationRecord record) {
        BigDecimal wages = record.getHoursWorked().multiply(record.getHourlyRate());
        record.setTotalAmount(wages.add(record.getCommissionAmount()).add(record.getBonusAmount()));
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private void assertValidPeriod(LocalDate start, LocalDate end) {
        if (end.isBefore(start)) {
            throw new BusinessException("COMPENSATION_RECORD_INVALID_PERIOD", "The pay period's end date must not be before its start date", HttpStatus.BAD_REQUEST);
        }
    }

    private CompensationRecord findOrThrow(UUID organizationId, UUID compensationRecordId) {
        return compensationRecordRepository.findActiveByIdAndOrganizationId(compensationRecordId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("CompensationRecord", compensationRecordId));
    }

    private UUID resolveOwner(UserPrincipal principal, Permission.Action action, UUID requestedOwnerId) {
        if (requestedOwnerId == null || requestedOwnerId.equals(principal.getId())) {
            return principal.getId();
        }
        if (scopeAuthorizationService.highestGranted(principal, RESOURCE, action) != ScopeAuthorizationService.Access.ORGANIZATION) {
            throw new ForbiddenException("You can only " + action.name().toLowerCase(Locale.ROOT) + " compensation records you manage");
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
}
