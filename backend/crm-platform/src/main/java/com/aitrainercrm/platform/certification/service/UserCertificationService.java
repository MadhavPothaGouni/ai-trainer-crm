package com.aitrainercrm.platform.certification.service;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.certification.dto.AwardCertificationRequest;
import com.aitrainercrm.platform.certification.entity.Certification;
import com.aitrainercrm.platform.certification.entity.UserCertification;
import com.aitrainercrm.platform.certification.repository.UserCertificationRepository;
import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.role.entity.Permission;
import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.user.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
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
 * One person's held {@link UserCertification} awards. Same OWN/TEAM/DEPARTMENT/ORGANIZATION shape
 * as {@code CourseEnrollmentService} - see its javadoc for the identical {@code resolveHolder}/
 * {@code resolveLearner} reasoning.
 */
@Service
@RequiredArgsConstructor
public class UserCertificationService {

    private static final Permission.Resource RESOURCE = Permission.Resource.USER_CERTIFICATION;

    private final UserCertificationRepository userCertificationRepository;
    private final CertificationService certificationService;
    private final UserRepository userRepository;
    private final ScopeAuthorizationService scopeAuthorizationService;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<UserCertification> list(UserPrincipal principal, Pageable pageable) {
        Optional<Set<UUID>> visibleUserIds = scopeAuthorizationService.visibleOwnerIds(principal, RESOURCE, Permission.Action.READ);
        return visibleUserIds
                .map(userIds -> userCertificationRepository.findByOrganizationIdAndUserIdInAndDeletedAtIsNullOrderByEarnedAtDesc(
                        principal.getOrganizationId(), userIds, pageable))
                .orElseGet(() -> userCertificationRepository.findByOrganizationIdAndDeletedAtIsNullOrderByEarnedAtDesc(
                        principal.getOrganizationId(), pageable));
    }

    @Transactional(readOnly = true)
    public UserCertification get(UserPrincipal principal, UUID userCertificationId) {
        UserCertification userCertification = findOrThrow(principal.getOrganizationId(), userCertificationId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.READ, userCertification.getUserId());
        return userCertification;
    }

    /**
     * Awards a certification. {@link UserCertification#getExpiresAt()} is derived once here from
     * {@link Certification#getValidityMonths()} - see {@link #computeExpiresAt}'s javadoc for why
     * this is a snapshot, not a live computation.
     */
    @Transactional
    public UserCertification award(UserPrincipal principal, AwardCertificationRequest request) {
        Certification certification = certificationService.findOrThrow(principal.getOrganizationId(), request.certificationId());
        UUID holderId = resolveHolder(principal, request.userId());

        UserCertification userCertification =
                new UserCertification(principal.getOrganizationId(), certification.getId(), holderId, request.earnedAt());
        userCertification.setCredentialNumber(request.credentialNumber());
        userCertification.setExpiresAt(computeExpiresAt(request.earnedAt(), certification.getValidityMonths()));
        userCertificationRepository.save(userCertification);

        events.publishEvent(new CrmAuditEvents.RecordCreated(
                principal.getId(), principal.getOrganizationId(), "UserCertification", userCertification.getId()));
        return userCertification;
    }

    @Transactional
    public UserCertification updateStatus(UserPrincipal principal, UUID userCertificationId, UserCertification.Status status, String notes) {
        UserCertification userCertification = findOrThrow(principal.getOrganizationId(), userCertificationId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, userCertification.getUserId());

        userCertification.setStatus(status);
        userCertification.setNotes(notes);
        userCertificationRepository.save(userCertification);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(
                principal.getId(), principal.getOrganizationId(), "UserCertification", userCertification.getId()));
        return userCertification;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID userCertificationId) {
        UserCertification userCertification = findOrThrow(principal.getOrganizationId(), userCertificationId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.DELETE, userCertification.getUserId());

        userCertification.setDeletedAt(Instant.now());
        userCertificationRepository.save(userCertification);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(
                principal.getId(), principal.getOrganizationId(), "UserCertification", userCertificationId));
    }

    /** Null validityMonths (never expires) yields a null expiresAt - see Certification#validityMonths' javadoc. Otherwise earnedAt + validityMonths, computed once and stored, never revisited if the Certification's own validityMonths later changes. */
    private LocalDate computeExpiresAt(LocalDate earnedAt, Integer validityMonths) {
        return validityMonths == null ? null : earnedAt.plusMonths(validityMonths);
    }

    private UserCertification findOrThrow(UUID organizationId, UUID userCertificationId) {
        return userCertificationRepository.findActiveByIdAndOrganizationId(userCertificationId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("UserCertification", userCertificationId));
    }

    private UUID resolveHolder(UserPrincipal principal, UUID requestedUserId) {
        if (requestedUserId == null || requestedUserId.equals(principal.getId())) {
            return principal.getId();
        }
        if (scopeAuthorizationService.highestGranted(principal, RESOURCE, Permission.Action.CREATE) != ScopeAuthorizationService.Access.ORGANIZATION) {
            throw new ForbiddenException("You can only record a certification for yourself");
        }
        assertUserInOrganization(principal.getOrganizationId(), requestedUserId);
        return requestedUserId;
    }

    private void assertUserInOrganization(UUID organizationId, UUID userId) {
        boolean exists = userRepository.findActiveById(userId).map(u -> organizationId.equals(u.getOrganizationId())).orElse(false);
        if (!exists) {
            throw new ResourceNotFoundException("User", userId);
        }
    }
}
