package com.aitrainercrm.platform.role.dto;

import com.aitrainercrm.platform.role.entity.Permission;
import java.util.UUID;
import lombok.Builder;

@Builder
public record PermissionDto(
        UUID id, String resource, String action, String scope, String description, String authorityName) {

    public static PermissionDto from(Permission permission) {
        return PermissionDto.builder()
                .id(permission.getId())
                .resource(permission.getResource().name())
                .action(permission.getAction().name())
                .scope(permission.getScope().name())
                .description(permission.getDescription())
                .authorityName(permission.toAuthorityName())
                .build();
    }
}
