package com.aitrainercrm.platform.certification.service;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.certification.dto.CreateCertificationRequest;
import com.aitrainercrm.platform.certification.dto.UpdateCertificationRequest;
import com.aitrainercrm.platform.certification.entity.Certification;
import com.aitrainercrm.platform.certification.repository.CertificationRepository;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The certification catalog. No {@link com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService}
 * calls - same reasoning {@link com.aitrainercrm.platform.course.service.CourseService}'s javadoc
 * gives for {@code Course}: no {@code ownerId} on {@link Certification}, so the controller's
 * TEAM/DEPARTMENT/ORGANIZATION {@code @PreAuthorize} is the whole authorization story.
 */
@Service
@RequiredArgsConstructor
public class CertificationService {

    private final CertificationRepository certificationRepository;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<Certification> list(UserPrincipal principal, Pageable pageable) {
        return certificationRepository.findByOrganizationIdAndDeletedAtIsNullOrderByNameAsc(principal.getOrganizationId(), pageable);
    }

    /** Unpaginated active catalog - same reasoning CourseService#listActive documents for its own equivalent. */
    @Transactional(readOnly = true)
    public List<Certification> listActive(UserPrincipal principal) {
        return certificationRepository.findByOrganizationIdAndActiveTrueAndDeletedAtIsNullOrderByNameAsc(principal.getOrganizationId());
    }

    @Transactional(readOnly = true)
    public Certification get(UserPrincipal principal, UUID certificationId) {
        return findOrThrow(principal.getOrganizationId(), certificationId);
    }

    @Transactional
    public Certification create(UserPrincipal principal, CreateCertificationRequest request) {
        Certification certification = new Certification(principal.getOrganizationId(), request.name());
        applyFields(certification, request.issuingBody(), request.description(), request.validityMonths());
        certificationRepository.save(certification);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "Certification", certification.getId()));
        return certification;
    }

    @Transactional
    public Certification update(UserPrincipal principal, UUID certificationId, UpdateCertificationRequest request) {
        Certification certification = findOrThrow(principal.getOrganizationId(), certificationId);
        certification.setName(request.name());
        certification.setActive(request.active());
        applyFields(certification, request.issuingBody(), request.description(), request.validityMonths());
        certificationRepository.save(certification);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "Certification", certification.getId()));
        return certification;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID certificationId) {
        Certification certification = findOrThrow(principal.getOrganizationId(), certificationId);
        certification.setDeletedAt(Instant.now());
        certificationRepository.save(certification);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "Certification", certificationId));
    }

    Certification findOrThrow(UUID organizationId, UUID certificationId) {
        return certificationRepository.findActiveByIdAndOrganizationId(certificationId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Certification", certificationId));
    }

    private void applyFields(Certification certification, String issuingBody, String description, Integer validityMonths) {
        certification.setIssuingBody(issuingBody);
        certification.setDescription(description);
        certification.setValidityMonths(validityMonths);
    }
}
