package com.aitrainercrm.platform.customfield.dto;

import com.aitrainercrm.platform.customfield.entity.CustomField;
import java.util.UUID;
import lombok.Builder;

/** A single field's definition joined with its (possibly absent) value on one record - what the frontend renders one form row from. */
@Builder
public record CustomFieldValueDto(
        UUID customFieldId, String apiName, String label, CustomField.FieldType fieldType, boolean required, String value) {
}
