package com.aitrainercrm.platform.emailtemplate.dto;

import com.aitrainercrm.platform.emailtemplate.entity.EmailTemplate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** category IS editable here, unlike SavedView's entityType or TerritoryRule's targetResource - a
 * template's category is just a filter tag, not a polymorphic "what this row targets" switch that
 * would invalidate other fields if it changed. */
public record UpdateEmailTemplateRequest(
        @NotBlank @Size(max = 150) String name,
        @NotNull EmailTemplate.Category category,
        @NotBlank @Size(max = 300) String subject,
        @NotBlank String body,
        boolean active) {
}
