package com.aitrainercrm.platform.sequence.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSequenceRequest(@NotBlank @Size(max = 200) String name, @Size(max = 2000) String description) {
}
