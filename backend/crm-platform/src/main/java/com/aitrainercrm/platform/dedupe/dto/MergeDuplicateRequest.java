package com.aitrainercrm.platform.dedupe.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** survivorId must be either the match's recordAId or recordBId - DuplicateMatchService rejects anything else with a 400. Whichever one it isn't becomes the absorbed record. */
public record MergeDuplicateRequest(@NotNull UUID survivorId) {
}
