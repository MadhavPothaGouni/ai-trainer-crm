package com.aitrainercrm.platform.membership.dto;

import com.aitrainercrm.platform.membership.entity.Membership;
import jakarta.validation.constraints.NotNull;

public record UpdateMembershipStatusRequest(@NotNull Membership.Status status) {
}
