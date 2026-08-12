package com.aitrainercrm.platform.approval.dto;

import jakarta.validation.constraints.Size;

/** Backs both POST .../approve and POST .../reject - comment is optional either way. */
public record DecideStepRequest(@Size(max = 1000) String comment) {
}
