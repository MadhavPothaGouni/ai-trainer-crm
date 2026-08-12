package com.aitrainercrm.platform.emailtemplate.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** No Spring context anywhere in this file - TemplateRenderer is pure string manipulation, the
 * whole reason it was split out of EmailTemplateService in the first place. */
class TemplateRendererTest {

    @Test
    void render_knownToken_isReplaced() {
        TemplateRenderer.Result result = TemplateRenderer.render("Hi {{contact.firstname}}!", Map.of("contact.firstname", "Ada"));

        assertThat(result.text()).isEqualTo("Hi Ada!");
        assertThat(result.unresolvedTokens()).isEmpty();
    }

    @Test
    void render_unknownToken_isLeftVerbatim_notBlanked() {
        TemplateRenderer.Result result = TemplateRenderer.render("Hi {{contact.firstname}}, re: {{opportunity.name}}", Map.of("contact.firstname", "Ada"));

        assertThat(result.text()).isEqualTo("Hi Ada, re: {{opportunity.name}}");
        assertThat(result.unresolvedTokens()).containsExactly("{{opportunity.name}}");
    }

    @Test
    void render_tokenNameIsCaseInsensitive() {
        TemplateRenderer.Result result = TemplateRenderer.render("Hi {{Contact.FirstName}}!", Map.of("contact.firstname", "Ada"));

        assertThat(result.text()).isEqualTo("Hi Ada!");
    }

    @Test
    void render_toleratesWhitespaceInsideBraces() {
        TemplateRenderer.Result result = TemplateRenderer.render("Hi {{ contact.firstname }}!", Map.of("contact.firstname", "Ada"));

        assertThat(result.text()).isEqualTo("Hi Ada!");
    }

    @Test
    void render_replacementValueContainingDollarSignsOrBackslashes_isNotMisinterpretedAsARegexGroupReference() {
        // A naive String#replaceAll("\\{\\{token}}", value) would choke on a value like "$100" or
        // a literal backslash - Matcher.quoteReplacement is what TemplateRenderer relies on to
        // treat the resolved value as completely literal text, never as a replacement pattern.
        TemplateRenderer.Result result = TemplateRenderer.render("Amount: {{opportunity.amount}}", Map.of("opportunity.amount", "$1,000\\undefined"));

        assertThat(result.text()).isEqualTo("Amount: $1,000\\undefined");
    }

    @Test
    void render_sameUnresolvedTokenAppearingTwice_isReportedOnce() {
        TemplateRenderer.Result result = TemplateRenderer.render("{{lead.companyname}} - {{lead.companyname}}", Map.of());

        assertThat(result.unresolvedTokens()).containsExactly("{{lead.companyname}}");
    }

    @Test
    void render_noTokensAtAll_returnsTextUnchanged() {
        TemplateRenderer.Result result = TemplateRenderer.render("Just plain text, no placeholders.", Map.of());

        assertThat(result.text()).isEqualTo("Just plain text, no placeholders.");
        assertThat(result.unresolvedTokens()).isEmpty();
    }

    @Test
    void render_emptyString_returnsEmptyResult() {
        TemplateRenderer.Result result = TemplateRenderer.render("", Map.of());

        assertThat(result.text()).isEmpty();
        assertThat(result.unresolvedTokens()).isEmpty();
    }

    @Test
    void render_malformedBraces_areNotTreatedAsTokens() {
        TemplateRenderer.Result result = TemplateRenderer.render("Hi {contact.firstname}, {{ }}, {{}}", Map.of("contact.firstname", "Ada"));

        assertThat(result.text()).isEqualTo("Hi {contact.firstname}, {{ }}, {{}}");
        assertThat(result.unresolvedTokens()).isEmpty();
    }
}
