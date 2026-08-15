package com.aitrainercrm.platform.equipment.service;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.equipment.dto.CreateEquipmentRequest;
import com.aitrainercrm.platform.equipment.dto.UpdateEquipmentRequest;
import com.aitrainercrm.platform.equipment.entity.Equipment;
import com.aitrainercrm.platform.equipment.repository.EquipmentRepository;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The equipment catalog. Exactly {@link com.aitrainercrm.platform.membership.service.MembershipPlanService}'s
 * shape - no {@link com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService}
 * calls here, since equipment has no {@code ownerId} (see {@link Equipment}'s javadoc); the
 * controller's {@code @PreAuthorize} (any of TEAM/DEPARTMENT/ORGANIZATION) is the whole
 * authorization story. {@link #findOrThrow} is package-private so {@code MaintenanceLogService}
 * can reuse it when validating a new log's parent equipment.
 */
@Service
@RequiredArgsConstructor
public class EquipmentService {

    private final EquipmentRepository equipmentRepository;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<Equipment> list(UserPrincipal principal, Pageable pageable) {
        return equipmentRepository.findByOrganizationIdAndDeletedAtIsNull(principal.getOrganizationId(), pageable);
    }

    @Transactional(readOnly = true)
    public Equipment get(UserPrincipal principal, UUID equipmentId) {
        return findOrThrow(principal.getOrganizationId(), equipmentId);
    }

    @Transactional
    public Equipment create(UserPrincipal principal, CreateEquipmentRequest request) {
        Equipment equipment = new Equipment(principal.getOrganizationId(), request.name());
        applyFields(
                equipment, request.category(), request.serialNumber(), request.location(), request.purchaseDate(), request.purchasePrice(), request.notes());
        equipmentRepository.save(equipment);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "Equipment", equipment.getId()));
        return equipment;
    }

    @Transactional
    public Equipment update(UserPrincipal principal, UUID equipmentId, UpdateEquipmentRequest request) {
        Equipment equipment = findOrThrow(principal.getOrganizationId(), equipmentId);
        equipment.setName(request.name());
        equipment.setStatus(request.status());
        applyFields(
                equipment, request.category(), request.serialNumber(), request.location(), request.purchaseDate(), request.purchasePrice(), request.notes());
        equipmentRepository.save(equipment);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "Equipment", equipment.getId()));
        return equipment;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID equipmentId) {
        Equipment equipment = findOrThrow(principal.getOrganizationId(), equipmentId);
        equipment.setDeletedAt(Instant.now());
        equipmentRepository.save(equipment);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "Equipment", equipmentId));
    }

    Equipment findOrThrow(UUID organizationId, UUID equipmentId) {
        return equipmentRepository.findActiveByIdAndOrganizationId(equipmentId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Equipment", equipmentId));
    }

    private void applyFields(
            Equipment equipment, String category, String serialNumber, String location, LocalDate purchaseDate, BigDecimal purchasePrice, String notes) {
        equipment.setCategory(category);
        equipment.setSerialNumber(serialNumber);
        equipment.setLocation(location);
        equipment.setPurchaseDate(purchaseDate);
        equipment.setPurchasePrice(purchasePrice);
        equipment.setNotes(notes);
    }
}
