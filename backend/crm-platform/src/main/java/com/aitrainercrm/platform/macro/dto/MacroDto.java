package com.aitrainercrm.platform.macro.dto;

import com.aitrainercrm.platform.macro.entity.Macro;
import com.aitrainercrm.platform.ticket.entity.Ticket;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record MacroDto(
        UUID id, String name, String body, Ticket.Status newStatus, boolean active, Instant createdAt, Instant updatedAt) {

    public static MacroDto from(Macro macro) {
        return MacroDto.builder()
                .id(macro.getId())
                .name(macro.getName())
                .body(macro.getBody())
                .newStatus(macro.getNewStatus())
                .active(macro.isActive())
                .createdAt(macro.getCreatedAt())
                .updatedAt(macro.getUpdatedAt())
                .build();
    }
}
