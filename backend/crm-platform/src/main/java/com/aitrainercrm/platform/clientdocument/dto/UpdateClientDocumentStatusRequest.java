package com.aitrainercrm.platform.clientdocument.dto;

import com.aitrainercrm.platform.clientdocument.entity.ClientDocument;
import jakarta.validation.constraints.NotNull;

public record UpdateClientDocumentStatusRequest(@NotNull ClientDocument.Status status) {
}
