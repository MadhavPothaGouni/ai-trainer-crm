package com.aitrainercrm.platform.email.dto;

import com.aitrainercrm.platform.email.entity.EmailMessage;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record EmailMessageDto(
        UUID id,
        EmailMessage.Direction direction,
        String subject,
        String body,
        String fromAddress,
        String toAddresses,
        String ccAddresses,
        EmailMessage.RelatedToType relatedToType,
        UUID relatedToId,
        Instant sentAt,
        UUID ownerId,
        Instant createdAt,
        Instant updatedAt) {

    public static EmailMessageDto from(EmailMessage email) {
        return EmailMessageDto.builder()
                .id(email.getId())
                .direction(email.getDirection())
                .subject(email.getSubject())
                .body(email.getBody())
                .fromAddress(email.getFromAddress())
                .toAddresses(email.getToAddresses())
                .ccAddresses(email.getCcAddresses())
                .relatedToType(email.getRelatedToType())
                .relatedToId(email.getRelatedToId())
                .sentAt(email.getSentAt())
                .ownerId(email.getOwnerId())
                .createdAt(email.getCreatedAt())
                .updatedAt(email.getUpdatedAt())
                .build();
    }
}
