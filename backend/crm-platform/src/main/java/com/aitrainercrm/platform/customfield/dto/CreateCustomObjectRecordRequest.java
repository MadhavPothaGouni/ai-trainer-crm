package com.aitrainercrm.platform.customfield.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;

/** {@code values} is optional - fieldId -> raw text value, set at creation time in the same call rather than requiring a follow-up PUT. */
public record CreateCustomObjectRecordRequest(@NotBlank @Size(max = 300) String name, Map<UUID, String> values) {
}
