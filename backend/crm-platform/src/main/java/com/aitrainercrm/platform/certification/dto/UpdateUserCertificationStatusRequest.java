package com.aitrainercrm.platform.certification.dto;

import com.aitrainercrm.platform.certification.entity.UserCertification;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateUserCertificationStatusRequest(@NotNull UserCertification.Status status, @Size(max = 1000) String notes) {
}
