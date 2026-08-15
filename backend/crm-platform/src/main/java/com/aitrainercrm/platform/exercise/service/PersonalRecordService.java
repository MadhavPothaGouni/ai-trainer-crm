package com.aitrainercrm.platform.exercise.service;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.common.exception.BusinessException;
import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.contact.repository.ContactRepository;
import com.aitrainercrm.platform.exercise.dto.CreatePersonalRecordRequest;
import com.aitrainercrm.platform.exercise.dto.UpdatePersonalRecordRequest;
import com.aitrainercrm.platform.exercise.entity.PersonalRecord;
import com.aitrainercrm.platform.exercise.repository.PersonalRecordRepository;
import com.aitrainercrm.platform.role.entity.Permission;
import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.user.repository.UserRepository;
import java.math.BigDecimal;
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
 * A client's best-ever result for one {@code Exercise} - see {@link PersonalRecord}'s javadoc and
 * V64's migration comment for the backstory. Follows the same OWN/TEAM/DEPARTMENT/ORGANIZATION
 * record-level authorization shape as {@code ClientGoalService}, with {@code resolveOwner}
 * defaulting a null {@code ownerId} to the caller. Injects {@link ExerciseService} and calls its
 * package-private {@code findOrThrow} to validate a new record's parent exercise - same
 * package-co-location precedent {@code RoomBookingService} established for {@code Room}.
 * {@link #assertIsImprovement} is the one piece of real business logic: a new record must beat the
 * contact's current best for that exact exercise+record-type combination, or the create/update is
 * rejected outright - the first "reject if not better than existing record" rule in this platform.
 */
@Service
@RequiredArgsConstructor
public class PersonalRecordService {

    private static final Permission.Resource RESOURCE = Permission.Resource.PERSONAL_RECORD;

    private final PersonalRecordRepository personalRecordRepository;
    private final ExerciseService exerciseService;
    private final ContactRepository contactRepository;
    private final UserRepository userRepository;
    private final ScopeAuthorizationService scopeAuthorizationService;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<PersonalRecord> list(UserPrincipal principal, Pageable pageable) {
        Optional<Set<UUID>> visibleOwnerIds = scopeAuthorizationService.visibleOwnerIds(principal, RESOURCE, Permission.Action.READ);
        return visibleOwnerIds
                .map(ownerIds -> personalRecordRepository.findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(principal.getOrganizationId(), ownerIds, pageable))
                .orElseGet(() -> personalRecordRepository.findByOrganizationIdAndDeletedAtIsNull(principal.getOrganizationId(), pageable));
    }

    @Transactional(readOnly = true)
    public PersonalRecord get(UserPrincipal principal, UUID personalRecordId) {
        PersonalRecord record = findOrThrow(principal.getOrganizationId(), personalRecordId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.READ, record.getOwnerId());
        return record;
    }

    @Transactional
    public PersonalRecord create(UserPrincipal principal, CreatePersonalRecordRequest request) {
        UUID ownerId = resolveOwner(principal, Permission.Action.CREATE, request.ownerId());
        exerciseService.findOrThrow(principal.getOrganizationId(), request.exerciseId());
        assertContactInOrganization(principal.getOrganizationId(), request.contactId());
        assertIsImprovement(request.contactId(), request.exerciseId(), request.recordType(), request.value(), null);

        PersonalRecord record = new PersonalRecord(
                principal.getOrganizationId(), request.contactId(), request.exerciseId(), ownerId, request.recordType(), request.value());
        if (request.achievedAt() != null) {
            record.setAchievedAt(request.achievedAt());
        }
        record.setNotes(request.notes());
        personalRecordRepository.save(record);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "PersonalRecord", record.getId()));
        return record;
    }

    @Transactional
    public PersonalRecord update(UserPrincipal principal, UUID personalRecordId, UpdatePersonalRecordRequest request) {
        PersonalRecord record = findOrThrow(principal.getOrganizationId(), personalRecordId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, record.getOwnerId());
        assertIsImprovement(record.getContactId(), record.getExerciseId(), record.getRecordType(), request.value(), record.getId());

        record.setValue(request.value());
        if (request.achievedAt() != null) {
            record.setAchievedAt(request.achievedAt());
        }
        record.setNotes(request.notes());
        personalRecordRepository.save(record);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "PersonalRecord", record.getId()));
        return record;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID personalRecordId) {
        PersonalRecord record = findOrThrow(principal.getOrganizationId(), personalRecordId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.DELETE, record.getOwnerId());

        record.setDeletedAt(Instant.now());
        personalRecordRepository.save(record);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "PersonalRecord", personalRecordId));
    }

    /**
     * Every {@link PersonalRecord.RecordType} shares "higher is better" semantics (see this
     * class's javadoc and V64's migration comment), so a single {@code value > currentBest}
     * comparison is universally correct here - no per-type direction logic needed. A contact with
     * no prior record for this exercise+type automatically passes (there's no best to beat yet).
     */
    private void assertIsImprovement(UUID contactId, UUID exerciseId, PersonalRecord.RecordType recordType, BigDecimal value, UUID excludeRecordId) {
        Optional<BigDecimal> currentBest = excludeRecordId == null
                ? personalRecordRepository.findBestValue(contactId, exerciseId, recordType)
                : personalRecordRepository.findBestValueExcluding(contactId, exerciseId, recordType, excludeRecordId);
        if (currentBest.isPresent() && value.compareTo(currentBest.get()) <= 0) {
            throw new BusinessException(
                    "PERSONAL_RECORD_NOT_AN_IMPROVEMENT",
                    "This value doesn't beat the client's current best of " + currentBest.get() + " for this exercise",
                    HttpStatus.CONFLICT);
        }
    }

    private PersonalRecord findOrThrow(UUID organizationId, UUID personalRecordId) {
        return personalRecordRepository.findActiveByIdAndOrganizationId(personalRecordId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("PersonalRecord", personalRecordId));
    }

    private UUID resolveOwner(UserPrincipal principal, Permission.Action action, UUID requestedOwnerId) {
        if (requestedOwnerId == null || requestedOwnerId.equals(principal.getId())) {
            return principal.getId();
        }
        if (scopeAuthorizationService.highestGranted(principal, RESOURCE, action) != ScopeAuthorizationService.Access.ORGANIZATION) {
            throw new ForbiddenException("You can only " + action.name().toLowerCase(Locale.ROOT) + " records you manage");
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
