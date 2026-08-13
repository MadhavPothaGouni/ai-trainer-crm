package com.aitrainercrm.platform.macro.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ApplyMacroRequest(@NotNull UUID ticketId) {
}
