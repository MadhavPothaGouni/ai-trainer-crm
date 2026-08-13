package com.aitrainercrm.platform.gdpr.dto;

import com.aitrainercrm.platform.contact.entity.Contact;
import com.aitrainercrm.platform.lead.entity.Lead;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

/** The full payload behind {@code POST /api/v1/data-subject-requests/export} - serialized straight to a downloadable JSON file by the controller, never wrapped in the usual {@code ApiResponse} envelope (see {@code DataSubjectRequestController}'s javadoc, same convention {@code ImportExportController}'s CSV exports already use for a different file format). */
@Builder
public record DataSubjectExportDto(String subjectEmail, Instant exportedAt, List<ExportedContact> contacts, List<ExportedLead> leads) {

    @Builder
    public record ExportedContact(
            UUID id, String firstName, String lastName, String email, String phone, String title, String description,
            UUID accountId, UUID ownerId, boolean deleted, Instant createdAt, Instant updatedAt) {

        public static ExportedContact from(Contact contact) {
            return ExportedContact.builder()
                    .id(contact.getId())
                    .firstName(contact.getFirstName())
                    .lastName(contact.getLastName())
                    .email(contact.getEmail())
                    .phone(contact.getPhone())
                    .title(contact.getTitle())
                    .description(contact.getDescription())
                    .accountId(contact.getAccountId())
                    .ownerId(contact.getOwnerId())
                    .deleted(contact.isDeleted())
                    .createdAt(contact.getCreatedAt())
                    .updatedAt(contact.getUpdatedAt())
                    .build();
        }
    }

    @Builder
    public record ExportedLead(
            UUID id, String firstName, String lastName, String email, String phone, String companyName, String title,
            Lead.Status status, String description, UUID ownerId, boolean deleted, Instant createdAt, Instant updatedAt) {

        public static ExportedLead from(Lead lead) {
            return ExportedLead.builder()
                    .id(lead.getId())
                    .firstName(lead.getFirstName())
                    .lastName(lead.getLastName())
                    .email(lead.getEmail())
                    .phone(lead.getPhone())
                    .companyName(lead.getCompanyName())
                    .title(lead.getTitle())
                    .status(lead.getStatus())
                    .description(lead.getDescription())
                    .ownerId(lead.getOwnerId())
                    .deleted(lead.isDeleted())
                    .createdAt(lead.getCreatedAt())
                    .updatedAt(lead.getUpdatedAt())
                    .build();
        }
    }
}
