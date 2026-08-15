package com.aitrainercrm.platform.intakeform.service;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.intakeform.dto.CreateIntakeFormRequest;
import com.aitrainercrm.platform.intakeform.dto.UpdateIntakeFormRequest;
import com.aitrainercrm.platform.intakeform.entity.IntakeForm;
import com.aitrainercrm.platform.intakeform.repository.IntakeFormRepository;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The intake-questionnaire catalog. Exactly {@link com.aitrainercrm.platform.room.service.RoomService}'s
 * shape - no {@link com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService}
 * calls here, since intake forms have no {@code ownerId} (see {@link IntakeForm}'s javadoc); the
 * controller's {@code @PreAuthorize} (any of TEAM/DEPARTMENT/ORGANIZATION) is the whole
 * authorization story. {@link #findOrThrow} is package-private so
 * {@code IntakeFormSubmissionService} can reuse it when validating a new submission's parent form.
 */
@Service
@RequiredArgsConstructor
public class IntakeFormService {

    private final IntakeFormRepository intakeFormRepository;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<IntakeForm> list(UserPrincipal principal, Pageable pageable) {
        return intakeFormRepository.findByOrganizationIdAndDeletedAtIsNull(principal.getOrganizationId(), pageable);
    }

    @Transactional(readOnly = true)
    public IntakeForm get(UserPrincipal principal, UUID intakeFormId) {
        return findOrThrow(principal.getOrganizationId(), intakeFormId);
    }

    @Transactional
    public IntakeForm create(UserPrincipal principal, CreateIntakeFormRequest request) {
        IntakeForm form = new IntakeForm(principal.getOrganizationId(), request.title());
        form.setFormType(request.formType() != null ? request.formType() : IntakeForm.FormType.OTHER);
        form.setNotes(request.notes());
        intakeFormRepository.save(form);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "IntakeForm", form.getId()));
        return form;
    }

    @Transactional
    public IntakeForm update(UserPrincipal principal, UUID intakeFormId, UpdateIntakeFormRequest request) {
        IntakeForm form = findOrThrow(principal.getOrganizationId(), intakeFormId);
        form.setTitle(request.title());
        form.setFormType(request.formType());
        form.setActive(request.active());
        form.setNotes(request.notes());
        intakeFormRepository.save(form);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "IntakeForm", form.getId()));
        return form;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID intakeFormId) {
        IntakeForm form = findOrThrow(principal.getOrganizationId(), intakeFormId);
        form.setDeletedAt(Instant.now());
        intakeFormRepository.save(form);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "IntakeForm", intakeFormId));
    }

    IntakeForm findOrThrow(UUID organizationId, UUID intakeFormId) {
        return intakeFormRepository.findActiveByIdAndOrganizationId(intakeFormId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("IntakeForm", intakeFormId));
    }
}
