package com.aitrainercrm.platform.sequence.dto;

import com.aitrainercrm.platform.sequence.entity.SequenceStep;
import java.util.UUID;
import lombok.Builder;

@Builder
public record SequenceStepDto(UUID id, int stepOrder, SequenceStep.Type type, int dayOffset, String subject, String body) {

    public static SequenceStepDto from(SequenceStep step) {
        return SequenceStepDto.builder()
                .id(step.getId())
                .stepOrder(step.getStepOrder())
                .type(step.getType())
                .dayOffset(step.getDayOffset())
                .subject(step.getSubject())
                .body(step.getBody())
                .build();
    }
}
