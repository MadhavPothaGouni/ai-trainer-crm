package com.aitrainercrm.platform.customfield.service;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.common.exception.BusinessException;
import com.aitrainercrm.platform.common.exception.DuplicateResourceException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.customfield.dto.CreateCustomFieldRequest;
import com.aitrainercrm.platform.customfield.dto.CustomFieldValueDto;
import com.aitrainercrm.platform.customfield.dto.SetCustomFieldValuesRequest;
import com.aitrainercrm.platform.customfield.dto.UpdateCustomFieldRequest;
import com.aitrainercrm.platform.customfield.entity.CustomField;
import com.aitrainercrm.platform.customfield.entity.CustomFieldValue;
import com.aitrainercrm.platform.customfield.repository.CustomFieldRepository;
import com.aitrainercrm.platform.customfield.repository.CustomFieldValueRepository;
import com.aitrainercrm.platform.customfield.repository.CustomObjectRepository;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD for custom field *definitions*, plus get/set of the actual
 * {@link CustomFieldValue} EAV rows for one record. Entirely gated by
 * CUSTOM_FIELD:*:ORGANIZATION - see V10's migration comment.
 *
 * <p>Value validation happens here, not on {@link CustomFieldValue} itself
 * (which just stores text): {@link #parseAndValidate} turns the raw string
 * an admin/user submitted into the right Java type for
 * {@link CustomField.FieldType#NUMBER}/{@code DATE}/{@code BOOLEAN}/
 * {@code PICKLIST} and re-serializes it back to text, rejecting anything
 * that doesn't parse (400, {@code CUSTOM_FIELD_INVALID_VALUE}) or - for
 * PICKLIST - isn't one of the field's declared {@code picklistValues}.
 * {@code required} is only enforced for fields explicitly present in a
 * {@link SetCustomFieldValuesRequest} with a blank/null value, not for
 * fields the caller simply didn't mention - a deliberate simplification
 * (documented here rather than silently assumed) that avoids forcing every
 * caller to resend every field's value on every partial update.
 */
@Service
@RequiredArgsConstructor
public class CustomFieldService {

    private final CustomFieldRepository customFieldRepository;
    private final CustomFieldValueRepository customFieldValueRepository;
    private final CustomObjectRepository customObjectRepository;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public List<CustomField> listForStandardEntity(UserPrincipal principal, CustomField.StandardEntityType standardEntityType) {
        return customFieldRepository.findByOrganizationIdAndStandardEntityTypeOrderByDisplayOrderAsc(
                principal.getOrganizationId(), standardEntityType);
    }

    @Transactional(readOnly = true)
    public List<CustomField> listForCustomObject(UserPrincipal principal, UUID customObjectId) {
        return customFieldRepository.findByOrganizationIdAndCustomObjectIdOrderByDisplayOrderAsc(
                principal.getOrganizationId(), customObjectId);
    }

    @Transactional(readOnly = true)
    public List<CustomField> listAll(UserPrincipal principal) {
        return customFieldRepository.findByOrganizationIdOrderByDisplayOrderAsc(principal.getOrganizationId());
    }

    @Transactional(readOnly = true)
    public CustomField get(UserPrincipal principal, UUID fieldId) {
        return findOrThrow(principal.getOrganizationId(), fieldId);
    }

    @Transactional
    public CustomField create(UserPrincipal principal, CreateCustomFieldRequest request) {
        UUID organizationId = principal.getOrganizationId();
        boolean hasStandard = request.standardEntityType() != null;
        boolean hasObject = request.customObjectId() != null;
        if (hasStandard == hasObject) {
            throw new BusinessException(
                    "CUSTOM_FIELD_INVALID_TARGET",
                    "Exactly one of standardEntityType or customObjectId must be set",
                    HttpStatus.BAD_REQUEST);
        }
        if (hasObject && customObjectRepository.findByIdAndOrganizationId(request.customObjectId(), organizationId).isEmpty()) {
            throw new ResourceNotFoundException("CustomObject", request.customObjectId());
        }
        boolean duplicate = hasStandard
                ? customFieldRepository.existsByOrganizationIdAndStandardEntityTypeAndApiName(
                        organizationId, request.standardEntityType(), request.apiName())
                : customFieldRepository.existsByOrganizationIdAndCustomObjectIdAndApiName(
                        organizationId, request.customObjectId(), request.apiName());
        if (duplicate) {
            throw new DuplicateResourceException("A custom field with api name '%s' already exists on this target".formatted(request.apiName()));
        }
        validatePicklistShape(request.fieldType(), request.picklistValues());

        CustomField field = new CustomField(
                organizationId, request.standardEntityType(), request.customObjectId(), request.apiName(), request.label(), request.fieldType());
        field.setRequired(request.required());
        field.setDisplayOrder(request.displayOrder());
        if (request.picklistValues() != null) {
            field.setPicklistValues(new ArrayList<>(request.picklistValues()));
        }
        customFieldRepository.save(field);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), organizationId, "CustomField", field.getId()));
        return field;
    }

    @Transactional
    public CustomField update(UserPrincipal principal, UUID fieldId, UpdateCustomFieldRequest request) {
        CustomField field = findOrThrow(principal.getOrganizationId(), fieldId);
        validatePicklistShape(field.getFieldType(), request.picklistValues());

        field.setLabel(request.label());
        field.setRequired(request.required());
        field.setDisplayOrder(request.displayOrder());
        field.setActive(Boolean.TRUE.equals(request.active()));
        field.getPicklistValues().clear();
        if (request.picklistValues() != null) {
            field.getPicklistValues().addAll(request.picklistValues());
        }
        customFieldRepository.save(field);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "CustomField", field.getId()));
        return field;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID fieldId) {
        CustomField field = findOrThrow(principal.getOrganizationId(), fieldId);
        customFieldRepository.delete(field);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "CustomField", fieldId));
    }

    /** Field defs (for whichever target) joined with any existing value on {@code recordId}. */
    @Transactional(readOnly = true)
    public List<CustomFieldValueDto> getValues(UserPrincipal principal, List<CustomField> fields, UUID recordId) {
        Map<UUID, String> existing = customFieldValueRepository.findByOrganizationIdAndRecordId(principal.getOrganizationId(), recordId).stream()
                .collect(java.util.stream.Collectors.toMap(CustomFieldValue::getCustomFieldId, CustomFieldValue::getValueText));
        return fields.stream()
                .map(field -> CustomFieldValueDto.builder()
                        .customFieldId(field.getId())
                        .apiName(field.getApiName())
                        .label(field.getLabel())
                        .fieldType(field.getFieldType())
                        .required(field.isRequired())
                        .value(existing.get(field.getId()))
                        .build())
                .toList();
    }

    @Transactional
    public List<CustomFieldValueDto> setValues(
            UserPrincipal principal, List<CustomField> fields, UUID recordId, SetCustomFieldValuesRequest request) {
        UUID organizationId = principal.getOrganizationId();
        Map<UUID, CustomField> fieldsById = fields.stream().collect(java.util.stream.Collectors.toMap(CustomField::getId, f -> f));

        for (Map.Entry<UUID, String> entry : request.values().entrySet()) {
            CustomField field = fieldsById.get(entry.getKey());
            if (field == null) {
                throw new ResourceNotFoundException("CustomField", entry.getKey());
            }
            String rawValue = entry.getValue();
            boolean blank = rawValue == null || rawValue.isBlank();
            if (blank && field.isRequired()) {
                throw new BusinessException(
                        "CUSTOM_FIELD_REQUIRED", "'%s' is required".formatted(field.getLabel()), HttpStatus.BAD_REQUEST);
            }
            String normalized = blank ? null : parseAndValidate(field, rawValue);

            if (normalized == null) {
                customFieldValueRepository.deleteByCustomFieldIdAndRecordId(field.getId(), recordId);
            } else {
                CustomFieldValue value = customFieldValueRepository
                        .findByCustomFieldIdAndRecordId(field.getId(), recordId)
                        .orElseGet(() -> new CustomFieldValue(organizationId, field.getId(), recordId, null));
                value.setValueText(normalized);
                customFieldValueRepository.save(value);
            }
        }

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), organizationId, "CustomFieldValue", recordId));
        return getValues(principal, fields, recordId);
    }

    /** Parses {@code rawValue} against {@code field.getFieldType()} and returns its canonical text form, or throws 400 if it doesn't fit the type. */
    private String parseAndValidate(CustomField field, String rawValue) {
        return switch (field.getFieldType()) {
            case TEXT, TEXT_AREA -> rawValue;
            case NUMBER -> {
                try {
                    yield new BigDecimal(rawValue).toPlainString();
                } catch (NumberFormatException e) {
                    throw invalidValue(field, "must be a number");
                }
            }
            case DATE -> {
                try {
                    yield LocalDate.parse(rawValue).toString();
                } catch (Exception e) {
                    throw invalidValue(field, "must be an ISO-8601 date (yyyy-MM-dd)");
                }
            }
            case BOOLEAN -> {
                if (!"true".equalsIgnoreCase(rawValue) && !"false".equalsIgnoreCase(rawValue)) {
                    throw invalidValue(field, "must be true or false");
                }
                yield rawValue.toLowerCase();
            }
            case PICKLIST -> {
                if (!field.getPicklistValues().contains(rawValue)) {
                    throw invalidValue(field, "must be one of " + field.getPicklistValues());
                }
                yield rawValue;
            }
        };
    }

    private void validatePicklistShape(CustomField.FieldType fieldType, List<String> picklistValues) {
        boolean hasValues = picklistValues != null && !picklistValues.isEmpty();
        if (fieldType == CustomField.FieldType.PICKLIST && !hasValues) {
            throw new BusinessException(
                    "CUSTOM_FIELD_PICKLIST_VALUES_REQUIRED", "PICKLIST fields need at least one picklist value", HttpStatus.BAD_REQUEST);
        }
        if (fieldType != CustomField.FieldType.PICKLIST && hasValues) {
            throw new BusinessException(
                    "CUSTOM_FIELD_PICKLIST_VALUES_NOT_ALLOWED", "Only PICKLIST fields may declare picklist values", HttpStatus.BAD_REQUEST);
        }
    }

    private BusinessException invalidValue(CustomField field, String reason) {
        return new BusinessException(
                "CUSTOM_FIELD_INVALID_VALUE", "Invalid value for '%s': %s".formatted(field.getLabel(), reason), HttpStatus.BAD_REQUEST);
    }

    private CustomField findOrThrow(UUID organizationId, UUID fieldId) {
        return customFieldRepository
                .findByIdAndOrganizationId(fieldId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("CustomField", fieldId));
    }
}
