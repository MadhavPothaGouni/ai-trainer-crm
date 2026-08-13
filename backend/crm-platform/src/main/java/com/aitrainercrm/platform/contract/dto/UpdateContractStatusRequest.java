package com.aitrainercrm.platform.contract.dto;

import com.aitrainercrm.platform.contract.entity.Contract;
import jakarta.validation.constraints.NotNull;

public record UpdateContractStatusRequest(@NotNull Contract.Status status) {
}
