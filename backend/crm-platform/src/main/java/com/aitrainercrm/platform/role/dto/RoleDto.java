package com.aitrainercrm.platform.role.dto;

import com.aitrainercrm.platform.role.entity.Role;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

@Builder
public record RoleDto(
        UUID id, String name, String description, boolean systemRole, List<PermissionDto> permissions) {

    public static RoleDto from(Role role) {
        return RoleDto.builder()
                .id(role.getId())
                .name(role.getName())
                .description(role.getDescription())
                .systemRole(role.isSystemRole())
                .permissions(role.getPermissions().stream().map(PermissionDto::from).toList())
                .build();
    }
}
