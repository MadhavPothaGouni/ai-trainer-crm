package com.aitrainercrm.platform.membership.dto;

import com.aitrainercrm.platform.membership.entity.MembershipFreeze;
import jakarta.validation.constraints.NotNull;

public record UpdateMembershipFreezeStatusRequest(@NotNull MembershipFreeze.Status status) {
}
