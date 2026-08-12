package com.aitrainercrm.platform.emailtemplate.dto;

import com.aitrainercrm.platform.emailtemplate.entity.EmailTemplate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateEmailTemplateRequest(
        @NotBlank @Size(max = 150) String name,
        @NotNull EmailTemplate.Category category,
        @NotBlank @Size(max = 300) String subject,
        @NotBlank String body) {
}
