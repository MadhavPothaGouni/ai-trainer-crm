package com.aitrainercrm.platform.shift.service;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.shift.dto.CreateShiftTemplateRequest;
import com.aitrainercrm.platform.shift.dto.UpdateShiftTemplateRequest;
import com.aitrainercrm.platform.shift.entity.ShiftTemplate;
import com.aitrainercrm.platform.shift.repository.ShiftTemplateRepository;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The recurring shift-pattern catalog. Exactly {@link com.aitrainercrm.platform.groupclass.service.GroupClassService}'s
 * shape - no {@link com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService}
 * calls here, since a template has no {@code ownerId} (see {@link ShiftTemplate}'s javadoc).
 */
@Service
@RequiredArgsConstructor
public class ShiftTemplateService {

    private final ShiftTemplateRepository shiftTemplateRepository;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<ShiftTemplate> list(UserPrincipal principal, Pageable pageable) {
        return shiftTemplateRepository.findByOrganizationIdAndDeletedAtIsNull(principal.getOrganizationId(), pageable);
    }

    @Transactional(readOnly = true)
    public ShiftTemplate get(UserPrincipal principal, UUID shiftTemplateId) {
        return findOrThrow(principal.getOrganizationId(), shiftTemplateId);
    }

    @Transactional
    public ShiftTemplate create(UserPrincipal principal, CreateShiftTemplateRequest request) {
        ShiftTemplate template = new ShiftTemplate(
                principal.getOrganizationId(), request.name(), request.dayOfWeek(), request.startTime(), request.endTime());
        template.setRole(request.role());
        shiftTemplateRepository.save(template);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "ShiftTemplate", template.getId()));
        return template;
    }

    @Transactional
    public ShiftTemplate update(UserPrincipal principal, UUID shiftTemplateId, UpdateShiftTemplateRequest request) {
        ShiftTemplate template = findOrThrow(principal.getOrganizationId(), shiftTemplateId);
        template.setName(request.name());
        template.setDayOfWeek(request.dayOfWeek());
        template.setStartTime(request.startTime());
        template.setEndTime(request.endTime());
        template.setRole(request.role());
        template.setActive(request.active());
        shiftTemplateRepository.save(template);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "ShiftTemplate", template.getId()));
        return template;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID shiftTemplateId) {
        ShiftTemplate template = findOrThrow(principal.getOrganizationId(), shiftTemplateId);
        template.setDeletedAt(Instant.now());
        shiftTemplateRepository.save(template);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "ShiftTemplate", shiftTemplateId));
    }

    private ShiftTemplate findOrThrow(UUID organizationId, UUID shiftTemplateId) {
        return shiftTemplateRepository.findActiveByIdAndOrganizationId(shiftTemplateId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("ShiftTemplate", shiftTemplateId));
    }
}
