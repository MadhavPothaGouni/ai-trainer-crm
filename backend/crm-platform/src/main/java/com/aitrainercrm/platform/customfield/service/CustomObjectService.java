package com.aitrainercrm.platform.customfield.service;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.common.exception.DuplicateResourceException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.customfield.dto.CreateCustomObjectRecordRequest;
import com.aitrainercrm.platform.customfield.dto.CreateCustomObjectRequest;
import com.aitrainercrm.platform.customfield.dto.UpdateCustomObjectRecordRequest;
import com.aitrainercrm.platform.customfield.dto.UpdateCustomObjectRequest;
import com.aitrainercrm.platform.customfield.entity.CustomObject;
import com.aitrainercrm.platform.customfield.entity.CustomObjectRecord;
import com.aitrainercrm.platform.customfield.repository.CustomFieldValueRepository;
import com.aitrainercrm.platform.customfield.repository.CustomObjectRecordRepository;
import com.aitrainercrm.platform.customfield.repository.CustomObjectRepository;
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
 * CRUD for custom object definitions and their records. Entirely gated by
 * CUSTOM_OBJECT:*:ORGANIZATION at the controller - see V10's migration
 * comment for why there's no OWN/TEAM/DEPARTMENT variant to check against
 * here (unlike, say, {@code OpportunityService}, there's no
 * ScopeAuthorizationService call anywhere in this class).
 *
 * <p>Field *values* on a record are handled by {@link CustomFieldService}
 * (which owns {@code CustomFieldValueRepository}), not here - this service
 * only knows about the record's one built-in {@code name} column. Deleting
 * an object cascades its records via V10's FK; deleting a single record is
 * a soft delete (see {@link CustomObjectRecord#deletedAt}) that also cleans
 * up its {@code CustomFieldValue} rows explicitly, since {@code record_id}
 * is untyped and can't cascade at the database level.
 */
@Service
@RequiredArgsConstructor
public class CustomObjectService {

    private final CustomObjectRepository customObjectRepository;
    private final CustomObjectRecordRepository customObjectRecordRepository;
    private final CustomFieldValueRepository customFieldValueRepository;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<CustomObject> list(UserPrincipal principal, Pageable pageable) {
        return customObjectRepository.findByOrganizationIdOrderByLabelAsc(principal.getOrganizationId(), pageable);
    }

    @Transactional(readOnly = true)
    public List<CustomObject> listActive(UserPrincipal principal) {
        return customObjectRepository.findByOrganizationIdAndActiveTrueOrderByLabelAsc(principal.getOrganizationId());
    }

    @Transactional(readOnly = true)
    public CustomObject get(UserPrincipal principal, UUID customObjectId) {
        return findOrThrow(principal.getOrganizationId(), customObjectId);
    }

    @Transactional
    public CustomObject create(UserPrincipal principal, CreateCustomObjectRequest request) {
        if (customObjectRepository.existsByOrganizationIdAndApiName(principal.getOrganizationId(), request.apiName())) {
            throw new DuplicateResourceException("A custom object with api name '%s' already exists".formatted(request.apiName()));
        }
        CustomObject object = new CustomObject(principal.getOrganizationId(), request.apiName(), request.label(), request.pluralLabel());
        object.setDescription(request.description());
        customObjectRepository.save(object);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "CustomObject", object.getId()));
        return object;
    }

    @Transactional
    public CustomObject update(UserPrincipal principal, UUID customObjectId, UpdateCustomObjectRequest request) {
        CustomObject object = findOrThrow(principal.getOrganizationId(), customObjectId);
        object.setLabel(request.label());
        object.setPluralLabel(request.pluralLabel());
        object.setDescription(request.description());
        object.setActive(Boolean.TRUE.equals(request.active()));
        customObjectRepository.save(object);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "CustomObject", object.getId()));
        return object;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID customObjectId) {
        CustomObject object = findOrThrow(principal.getOrganizationId(), customObjectId);
        customObjectRepository.delete(object);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "CustomObject", customObjectId));
    }

    @Transactional(readOnly = true)
    public Page<CustomObjectRecord> listRecords(UserPrincipal principal, UUID customObjectId, Pageable pageable) {
        findOrThrow(principal.getOrganizationId(), customObjectId);
        return customObjectRecordRepository.findByCustomObjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(customObjectId, pageable);
    }

    @Transactional(readOnly = true)
    public CustomObjectRecord getRecord(UserPrincipal principal, UUID customObjectId, UUID recordId) {
        findOrThrow(principal.getOrganizationId(), customObjectId);
        return findRecordOrThrow(customObjectId, recordId);
    }

    @Transactional
    public CustomObjectRecord createRecord(UserPrincipal principal, UUID customObjectId, CreateCustomObjectRecordRequest request) {
        CustomObject object = findOrThrow(principal.getOrganizationId(), customObjectId);
        CustomObjectRecord record = new CustomObjectRecord(object.getId(), principal.getOrganizationId(), request.name());
        customObjectRecordRepository.save(record);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "CustomObjectRecord", record.getId()));
        return record;
    }

    @Transactional
    public CustomObjectRecord updateRecord(
            UserPrincipal principal, UUID customObjectId, UUID recordId, UpdateCustomObjectRecordRequest request) {
        findOrThrow(principal.getOrganizationId(), customObjectId);
        CustomObjectRecord record = findRecordOrThrow(customObjectId, recordId);
        record.setName(request.name());
        customObjectRecordRepository.save(record);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "CustomObjectRecord", record.getId()));
        return record;
    }

    @Transactional
    public void deleteRecord(UserPrincipal principal, UUID customObjectId, UUID recordId) {
        findOrThrow(principal.getOrganizationId(), customObjectId);
        CustomObjectRecord record = findRecordOrThrow(customObjectId, recordId);
        record.setDeletedAt(Instant.now());
        customObjectRecordRepository.save(record);
        customFieldValueRepository.deleteByRecordId(recordId);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "CustomObjectRecord", recordId));
    }

    private CustomObject findOrThrow(UUID organizationId, UUID customObjectId) {
        return customObjectRepository
                .findByIdAndOrganizationId(customObjectId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("CustomObject", customObjectId));
    }

    private CustomObjectRecord findRecordOrThrow(UUID customObjectId, UUID recordId) {
        return customObjectRecordRepository
                .findByIdAndCustomObjectIdAndDeletedAtIsNull(recordId, customObjectId)
                .orElseThrow(() -> new ResourceNotFoundException("CustomObjectRecord", recordId));
    }
}
