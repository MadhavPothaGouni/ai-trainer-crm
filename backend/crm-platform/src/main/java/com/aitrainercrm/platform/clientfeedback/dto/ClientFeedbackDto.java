package com.aitrainercrm.platform.clientfeedback.dto;

import com.aitrainercrm.platform.clientfeedback.entity.ClientFeedback;
import java.time.Instant;
import java.util.UUID;

public record ClientFeedbackDto(
        UUID id,
        UUID contactId,
        UUID ownerId,
        Integer npsScore,
        ClientFeedback.RelatedType relatedType,
        Instant submittedAt,
        String comments,
        Instant createdAt,
        Instant updatedAt) {

    public static ClientFeedbackDto from(ClientFeedback feedback) {
        return new ClientFeedbackDto(
                feedback.getId(),
                feedback.getContactId(),
                feedback.getOwnerId(),
                feedback.getNpsScore(),
                feedback.getRelatedType(),
                feedback.getSubmittedAt(),
                feedback.getComments(),
                feedback.getCreatedAt(),
                feedback.getUpdatedAt());
    }
}
