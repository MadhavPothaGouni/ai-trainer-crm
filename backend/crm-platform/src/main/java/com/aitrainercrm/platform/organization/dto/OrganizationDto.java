package com.aitrainercrm.platform.organization.dto;

import com.aitrainercrm.platform.organization.entity.Organization;
import java.util.UUID;
import lombok.Builder;

@Builder
public record OrganizationDto(
        UUID id, String name, String slug, String defaultCurrency, String timezone, int fiscalYearStartMonth) {

    public static OrganizationDto from(Organization organization) {
        return OrganizationDto.builder()
                .id(organization.getId())
                .name(organization.getName())
                .slug(organization.getSlug())
                .defaultCurrency(organization.getDefaultCurrency())
                .timezone(organization.getTimezone())
                .fiscalYearStartMonth(organization.getFiscalYearStartMonth())
                .build();
    }
}
