package com.aitrainercrm.platform.account.dto;

import com.aitrainercrm.platform.account.entity.Account;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record AccountDto(
        UUID id,
        String name,
        String industry,
        String website,
        String phone,
        String billingStreet,
        String billingCity,
        String billingState,
        String billingPostalCode,
        String billingCountry,
        BigDecimal annualRevenue,
        Integer employeeCount,
        String description,
        UUID ownerId,
        Instant createdAt,
        Instant updatedAt) {

    public static AccountDto from(Account account) {
        return AccountDto.builder()
                .id(account.getId())
                .name(account.getName())
                .industry(account.getIndustry())
                .website(account.getWebsite())
                .phone(account.getPhone())
                .billingStreet(account.getBillingStreet())
                .billingCity(account.getBillingCity())
                .billingState(account.getBillingState())
                .billingPostalCode(account.getBillingPostalCode())
                .billingCountry(account.getBillingCountry())
                .annualRevenue(account.getAnnualRevenue())
                .employeeCount(account.getEmployeeCount())
                .description(account.getDescription())
                .ownerId(account.getOwnerId())
                .createdAt(account.getCreatedAt())
                .updatedAt(account.getUpdatedAt())
                .build();
    }
}
