package com.aitrainercrm.platform.emailtemplate.dto;

import java.util.List;

/** {@code unresolvedTokens} lists every {@code {{token}}} still present in the merged output
 * verbatim (e.g. the caller asked to merge against a Lead but the template also references
 * {{opportunity.name}}) - {@code EmailTemplateService#render} never fails a request over this, it
 * just tells the caller what it couldn't fill in so a compose UI can flag it before sending. */
public record RenderedEmailDto(String subject, String body, List<String> unresolvedTokens) {
}
