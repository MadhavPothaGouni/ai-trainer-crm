package com.aitrainercrm.platform.clientdocument.dto;

import com.aitrainercrm.platform.clientdocument.entity.ClientDocument;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record CreateClientDocumentRequest(
        @NotNull UUID contactId,
        @NotNull ClientDocument.DocumentType documentType,
        @NotBlank @Size(max = 200) String title,
        LocalDate expiresAt,
        @Size(max = 2000) String fileUrl,
        @Size(max = 2000) String notes,

        /** Null defaults to the creator - see AccountService#resolveOwner for the identical rule applied here. */
        UUID ownerId) {
}
