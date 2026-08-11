package com.aitrainercrm.platform.customfield.dto;

import com.aitrainercrm.platform.customfield.entity.CustomField;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

@Builder
public record CustomFieldDto(
        UUID id,
        CustomField.StandardEntityType standardEntityType,
        UUID customObjectId,
        String apiName,
        String label,
        CustomField.FieldType fieldType,
        boolean required,
        int displayOrder,
        boolean active,
        List<String> picklistValues) {

    public static CustomFieldDto from(CustomField field) {
        return CustomFieldDto.builder()
                .id(field.getId())
                .standardEntityType(field.getStandardEntityType())
                .customObjectId(field.getCustomObjectId())
                .apiName(field.getApiName())
                .label(field.getLabel())
                .fieldType(field.getFieldType())
                .required(field.isRequired())
                .displayOrder(field.getDisplayOrder())
                .active(field.isActive())
                .picklistValues(field.getPicklistValues())
                .build();
    }
}
