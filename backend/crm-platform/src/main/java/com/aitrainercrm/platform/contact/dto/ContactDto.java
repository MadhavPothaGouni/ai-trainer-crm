package com.aitrainercrm.platform.contact.dto;

import com.aitrainercrm.platform.contact.entity.Contact;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record ContactDto(
        UUID id,
        String firstName,
        String lastName,
        String fullName,
        String email,
        String phone,
        String title,
        String description,
        UUID accountId,
        UUID ownerId,
        Instant createdAt,
        Instant updatedAt) {

    public static ContactDto from(Contact contact) {
        return ContactDto.builder()
                .id(contact.getId())
                .firstName(contact.getFirstName())
                .lastName(contact.getLastName())
                .fullName(contact.getFullName())
                .email(contact.getEmail())
                .phone(contact.getPhone())
                .title(contact.getTitle())
                .description(contact.getDescription())
                .accountId(contact.getAccountId())
                .ownerId(contact.getOwnerId())
                .createdAt(contact.getCreatedAt())
                .updatedAt(contact.getUpdatedAt())
                .build();
    }
}
