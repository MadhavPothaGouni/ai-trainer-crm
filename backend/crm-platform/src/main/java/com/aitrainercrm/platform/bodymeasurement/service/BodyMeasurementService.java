package com.aitrainercrm.platform.bodymeasurement.service;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.bodymeasurement.dto.CreateBodyMeasurementRequest;
import com.aitrainercrm.platform.bodymeasurement.dto.UpdateBodyMeasurementRequest;
import com.aitrainercrm.platform.bodymeasurement.entity.BodyMeasurement;
import com.aitrainercrm.platform.bodymeasurement.repository.BodyMeasurementRepository;
import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.contact.repository.ContactRepository;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Body measurements - see {@link BodyMeasurement}'s javadoc and V41's migration comment for the
 * backstory. Follows the exact same shape as {@code NutritionPlanService}/{@code
 * ClientGoalService}: OWN/TEAM/DEPARTMENT/ORGANIZATION record-level authorization via {@link
 * ScopeAuthorizationService}, {@code resolveOwner} defaulting a null {@code ownerId} to the
 * caller. No {@code updateStatus} method here, unlike those two - this module has no status
 * field at all (see the entity javadoc for why).
 */
@Service
@RequiredArgsConstructor
public class BodyMeasurementService {

    private static final Permission.Resource RESOURCE = Permission.Resource.BODY_MEASUREMENT;

    private final BodyMeasurementRepository bodyMeasurementRepository;
    private final ContactRepository contactRepository;
    private final UserRepository userRepository;
    private final ScopeAuthorizationService scopeAuthorizationService;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<BodyMeasurement> list(UserPrincipal principal, Pageable pageable) {
        Optional<Set<UUID>> visibleOwnerIds = scopeAuthorizationService.visibleOwnerIds(principal, RESOURCE, Permission.Action.READ);
        return visibleOwnerIds
                .map(ownerIds -> bodyMeasurementRepository.findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(principal.getOrganizationId(), ownerIds, pageable))
                .orElseGet(() -> bodyMeasurementRepository.findByOrganizationIdAndDeletedAtIsNull(principal.getOrganizationId(), pageable));
    }

    @Transactional(readOnly = true)
    public BodyMeasurement get(UserPrincipal principal, UUID bodyMeasurementId) {
        BodyMeasurement measurement = findOrThrow(principal.getOrganizationId(), bodyMeasurementId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.READ, measurement.getOwnerId());
        return measurement;
    }

    @Transactional
    public BodyMeasurement create(UserPrincipal principal, CreateBodyMeasurementRequest request) {
        UUID ownerId = resolveOwner(principal, Permission.Action.CREATE, request.ownerId());
        assertContactInOrganization(principal.getOrganizationId(), request.contactId());

        BodyMeasurement measurement = new BodyMeasurement(principal.getOrganizationId(), request.contactId(), ownerId, request.measuredAt());
        measurement.setWeightValue(request.weightValue());
        measurement.setWeightUnit(request.weightUnit());
        measurement.setBodyFatPercent(request.bodyFatPercent());
        measurement.setChestCm(request.chestCm());
        measurement.setWaistCm(request.waistCm());
        measurement.setHipsCm(request.hipsCm());
        measurement.setNotes(request.notes());
        bodyMeasurementRepository.save(measurement);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "BodyMeasurement", measurement.getId()));
        return measurement;
    }

    @Transactional
    public BodyMeasurement update(UserPrincipal principal, UUID bodyMeasurementId, UpdateBodyMeasurementRequest request) {
        BodyMeasurement measurement = findOrThrow(principal.getOrganizationId(), bodyMeasurementId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, measurement.getOwnerId());

        measurement.setMeasuredAt(request.measuredAt());
        measurement.setWeightValue(request.weightValue());
        measurement.setWeightUnit(request.weightUnit());
        measurement.setBodyFatPercent(request.bodyFatPercent());
        measurement.setChestCm(request.chestCm());
        measurement.setWaistCm(request.waistCm());
        measurement.setHipsCm(request.hipsCm());
        measurement.setNotes(request.notes());
        bodyMeasurementRepository.save(measurement);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "BodyMeasurement", measurement.getId()));
        return measurement;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID bodyMeasurementId) {
        BodyMeasurement measurement = findOrThrow(principal.getOrganizationId(), bodyMeasurementId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.DELETE, measurement.getOwnerId());

        measurement.setDeletedAt(Instant.now());
        bodyMeasurementRepository.save(measurement);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "BodyMeasurement", measurement.getId()));
    }

    private BodyMeasurement findOrThrow(UUID organizationId, UUID bodyMeasurementId) {
        return bodyMeasurementRepository.findActiveByIdAndOrganizationId(bodyMeasurementId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("BodyMeasurement", bodyMeasurementId));
    }

    private UUID resolveOwner(UserPrincipal principal, Permission.Action action, UUID requestedOwnerId) {
        if (requestedOwnerId == null || requestedOwnerId.equals(principal.getId())) {
            return principal.getId();
        }
        if (scopeAuthorizationService.highestGranted(principal, RESOURCE, action) != ScopeAuthorizationService.Access.ORGANIZATION) {
            throw new ForbiddenException("You can only " + action.name().toLowerCase(Locale.ROOT) + " records assigned to yourself");
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
