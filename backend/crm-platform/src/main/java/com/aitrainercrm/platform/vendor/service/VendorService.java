package com.aitrainercrm.platform.vendor.service;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.vendor.dto.CreateVendorRequest;
import com.aitrainercrm.platform.vendor.dto.UpdateVendorRequest;
import com.aitrainercrm.platform.vendor.entity.Vendor;
import com.aitrainercrm.platform.vendor.repository.VendorRepository;
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
 * The vendor catalog. Exactly {@link com.aitrainercrm.platform.equipment.service.EquipmentService}'s
 * shape - no {@link com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService}
 * calls here, since vendors have no {@code ownerId} (see {@link Vendor}'s javadoc); the
 * controller's {@code @PreAuthorize} (any of TEAM/DEPARTMENT/ORGANIZATION) is the whole
 * authorization story. {@link #findOrThrow} is package-private so {@code PurchaseOrderService}
 * can reuse it when validating a new order's parent vendor.
 */
@Service
@RequiredArgsConstructor
public class VendorService {

    private final VendorRepository vendorRepository;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<Vendor> list(UserPrincipal principal, Pageable pageable) {
        return vendorRepository.findByOrganizationIdAndDeletedAtIsNull(principal.getOrganizationId(), pageable);
    }

    @Transactional(readOnly = true)
    public Vendor get(UserPrincipal principal, UUID vendorId) {
        return findOrThrow(principal.getOrganizationId(), vendorId);
    }

    @Transactional
    public Vendor create(UserPrincipal principal, CreateVendorRequest request) {
        Vendor vendor = new Vendor(principal.getOrganizationId(), request.name());
        applyFields(vendor, request.contactName(), request.email(), request.phone(), request.category(), request.notes());
        vendorRepository.save(vendor);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "Vendor", vendor.getId()));
        return vendor;
    }

    @Transactional
    public Vendor update(UserPrincipal principal, UUID vendorId, UpdateVendorRequest request) {
        Vendor vendor = findOrThrow(principal.getOrganizationId(), vendorId);
        vendor.setName(request.name());
        vendor.setActive(request.active());
        applyFields(vendor, request.contactName(), request.email(), request.phone(), request.category(), request.notes());
        vendorRepository.save(vendor);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "Vendor", vendor.getId()));
        return vendor;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID vendorId) {
        Vendor vendor = findOrThrow(principal.getOrganizationId(), vendorId);
        vendor.setDeletedAt(Instant.now());
        vendorRepository.save(vendor);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "Vendor", vendorId));
    }

    Vendor findOrThrow(UUID organizationId, UUID vendorId) {
        return vendorRepository.findActiveByIdAndOrganizationId(vendorId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor", vendorId));
    }

    private void applyFields(Vendor vendor, String contactName, String email, String phone, String category, String notes) {
        vendor.setContactName(contactName);
        vendor.setEmail(email);
        vendor.setPhone(phone);
        vendor.setCategory(category);
        vendor.setNotes(notes);
    }
}
