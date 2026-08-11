package com.aitrainercrm.platform.customfield.dto;

import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;

/** fieldId -> raw text value. A {@code null} (or absent-then-omitted) entry for a field clears its value for this record. */
public record SetCustomFieldValuesRequest(@NotNull Map<UUID, String> values) {
}
