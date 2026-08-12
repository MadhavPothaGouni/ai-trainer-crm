package com.aitrainercrm.platform.emailtemplate.dto;

import java.util.UUID;

/**
 * Every field is optional and independent - a caller merging a template against just a Lead sends
 * only {@code leadId}, one merging against an Opportunity being worked by a known Contact can send
 * both {@code contactId} and {@code opportunityId} together, and so on. {@code
 * EmailTemplateService#render} only ever resolves the {@code {{prefix.*}}} tokens whose id was
 * actually supplied; every other token family's placeholders are left untouched in the output - see
 * {@code TemplateRenderer}'s javadoc.
 */
public record RenderEmailTemplateRequest(UUID contactId, UUID leadId, UUID accountId, UUID opportunityId) {
}
