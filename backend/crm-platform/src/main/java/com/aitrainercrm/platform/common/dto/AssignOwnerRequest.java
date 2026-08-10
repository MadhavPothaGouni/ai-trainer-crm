package com.aitrainercrm.platform.common.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Shared request shape for every CRM entity's "reassign owner" endpoint (account/contact/lead/opportunity) - gated on the ASSIGN action, not UPDATE. */
public record AssignOwnerRequest(@NotNull UUID ownerId) {
}
