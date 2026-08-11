package com.aitrainercrm.platform.organization.dto;

import com.aitrainercrm.platform.organization.entity.Team;
import java.util.UUID;
import lombok.Builder;

@Builder
public record TeamDto(UUID id, String name, String department, UUID leadUserId) {

    public static TeamDto from(Team team) {
        return TeamDto.builder()
                .id(team.getId())
                .name(team.getName())
                .department(team.getDepartment())
                .leadUserId(team.getLeadUserId())
                .build();
    }
}
