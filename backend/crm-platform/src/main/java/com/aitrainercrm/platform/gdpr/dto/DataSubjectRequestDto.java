package com.aitrainercrm.platform.gdpr.dto;

import com.aitrainercrm.platform.gdpr.entity.DataSubjectRequest;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record DataSubjectRequestDto(
        UUID id,
        DataSubjectRequest.RequestType requestType,
        String subjectEmail,
        DataSubjectRequest.Status status,
        UUID initiatedByUserId,
        int contactsAffected,
        int leadsAffected,
        String resultNote,
        Instant completedAt,
        Instant createdAt) {

    public static DataSubjectRequestDto from(DataSubjectRequest request) {
        return DataSubjectRequestDto.builder()
                .id(request.getId())
                .requestType(request.getRequestType())
                .subjectEmail(request.getSubjectEmail())
                .status(request.getStatus())
                .initiatedByUserId(request.getInitiatedByUserId())
                .contactsAffected(request.getContactsAffected())
                .leadsAffected(request.getLeadsAffected())
                .resultNote(request.getResultNote())
                .completedAt(request.getCompletedAt())
                .createdAt(request.getCreatedAt())
                .build();
    }
}
