package com.aitrainercrm.platform.groupclass.dto;

import jakarta.validation.constraints.Size;

public record UpdateClassWaitlistRequest(@Size(max = 2000) String notes) {
}
