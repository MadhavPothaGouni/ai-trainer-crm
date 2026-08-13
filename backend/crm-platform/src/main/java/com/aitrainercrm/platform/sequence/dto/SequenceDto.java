package com.aitrainercrm.platform.sequence.dto;

import com.aitrainercrm.platform.sequence.entity.Sequence;
import com.aitrainercrm.platform.sequence.entity.SequenceStep;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

@Builder
public record SequenceDto(
        UUID id, String name, String description, boolean active, List<SequenceStepDto> steps, Instant createdAt, Instant updatedAt) {

    public static SequenceDto from(Sequence sequence, List<SequenceStep> steps) {
        return SequenceDto.builder()
                .id(sequence.getId())
                .name(sequence.getName())
                .description(sequence.getDescription())
                .active(sequence.isActive())
                .steps(steps.stream().map(SequenceStepDto::from).toList())
                .createdAt(sequence.getCreatedAt())
                .updatedAt(sequence.getUpdatedAt())
                .build();
    }
}
