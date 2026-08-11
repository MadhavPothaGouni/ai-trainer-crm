package com.aitrainercrm.platform.customfield.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateCustomObjectRecordRequest(@NotBlank @Size(max = 300) String name) {
}
