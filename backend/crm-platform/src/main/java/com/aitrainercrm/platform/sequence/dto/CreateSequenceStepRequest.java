package com.aitrainercrm.platform.sequence.dto;

import com.aitrainercrm.platform.sequence.entity.SequenceStep;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateSequenceStepRequest(
        @NotNull SequenceStep.Type type, @Min(0) int dayOffset, @Size(max = 200) String subject, @Size(max = 4000) String body) {
}
