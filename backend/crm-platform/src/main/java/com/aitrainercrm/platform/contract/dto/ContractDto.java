package com.aitrainercrm.platform.contract.dto;

import com.aitrainercrm.platform.contract.entity.Contract;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Builder;

@Builder
public record ContractDto(
        UUID id,
        UUID accountId,
        UUID opportunityId,
        UUID ownerId,
        String contractNumber,
        String title,
        Contract.Status status,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal totalValue,
        boolean autoRenew,
        Integer renewalTermMonths,
        Instant signedAt,
        String terms,
        Instant createdAt,
        Instant updatedAt) {

    public static ContractDto from(Contract contract) {
        return ContractDto.builder()
                .id(contract.getId())
                .accountId(contract.getAccountId())
                .opportunityId(contract.getOpportunityId())
                .ownerId(contract.getOwnerId())
                .contractNumber(contract.getContractNumber())
                .title(contract.getTitle())
                .status(contract.getStatus())
                .startDate(contract.getStartDate())
                .endDate(contract.getEndDate())
                .totalValue(contract.getTotalValue())
                .autoRenew(contract.isAutoRenew())
                .renewalTermMonths(contract.getRenewalTermMonths())
                .signedAt(contract.getSignedAt())
                .terms(contract.getTerms())
                .createdAt(contract.getCreatedAt())
                .updatedAt(contract.getUpdatedAt())
                .build();
    }
}
