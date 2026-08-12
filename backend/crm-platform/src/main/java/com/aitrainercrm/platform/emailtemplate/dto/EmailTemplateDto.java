package com.aitrainercrm.platform.emailtemplate.dto;

import com.aitrainercrm.platform.emailtemplate.entity.EmailTemplate;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record EmailTemplateDto(
        UUID id,
        String name,
        EmailTemplate.Category category,
        String subject,
        String body,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public static EmailTemplateDto from(EmailTemplate template) {
        return EmailTemplateDto.builder()
                .id(template.getId())
                .name(template.getName())
                .category(template.getCategory())
                .subject(template.getSubject())
                .body(template.getBody())
                .active(template.isActive())
                .createdAt(template.getCreatedAt())
                .updatedAt(template.getUpdatedAt())
                .build();
    }
}
