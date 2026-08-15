package com.aitrainercrm.platform.clientdocument.dto;

import com.aitrainercrm.platform.clientdocument.entity.ClientDocument;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Status is deliberately not editable here - see UpdateClientDocumentStatusRequest / PATCH .../status, same reasoning UpdateReferralRequest documents. */
public record UpdateClientDocumentRequest(
        @NotNull ClientDocument.DocumentType documentType,
        @NotBlank @Size(max = 200) String title,
        LocalDate expiresAt,
        @Size(max = 2000) String fileUrl,
        @Size(max = 2000) String notes) {
}
