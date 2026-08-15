package com.aitrainercrm.platform.clientdocument.dto;

import com.aitrainercrm.platform.clientdocument.entity.ClientDocument;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ClientDocumentDto(
        UUID id,
        UUID contactId,
        UUID ownerId,
        ClientDocument.DocumentType documentType,
        String title,
        ClientDocument.Status status,
        Instant signedAt,
        LocalDate expiresAt,
        String fileUrl,
        String notes,
        Instant createdAt,
        Instant updatedAt) {

    public static ClientDocumentDto from(ClientDocument document) {
        return new ClientDocumentDto(
                document.getId(),
                document.getContactId(),
                document.getOwnerId(),
                document.getDocumentType(),
                document.getTitle(),
                document.getStatus(),
                document.getSignedAt(),
                document.getExpiresAt(),
                document.getFileUrl(),
                document.getNotes(),
                document.getCreatedAt(),
                document.getUpdatedAt());
    }
}
