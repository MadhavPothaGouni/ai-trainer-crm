package com.aitrainercrm.platform.emailtemplate.render;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stateless {@code {{token}}} substitution - the actual mail-merge mechanics behind {@code
 * EmailTemplateService#render}, split into its own class specifically so it can be unit-tested
 * without a database, an org, or any real Contact/Lead/Account/Opportunity row at all.
 *
 * <p>Deliberately dumb by design: it knows nothing about Contact/Lead/Account/Opportunity, only a
 * flat, already-resolved {@code Map<String, String>} of token name (lowercase, e.g. {@code
 * "contact.firstname"}) to value - {@code EmailTemplateService} owns the "which entity types exist
 * and how do their fields map to token names" knowledge, this class owns nothing but regex and
 * string replacement.
 *
 * <p>A token whose name isn't a key in {@code tokenValues} - because the caller never supplied that
 * entity id, or because the template simply has a typo - is left in the output exactly as written
 * ({@code "{{lead.doesNotExist}}"} stays literal text) rather than being silently blanked out or
 * causing the whole render to fail. A blank replacement would be indistinguishable from "this field
 * really is empty on the record," which is a much worse failure mode for an email a human is about
 * to send than a visibly unresolved placeholder they'll immediately notice and fix.
 */
public final class TemplateRenderer {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\{\\{\\s*([a-zA-Z][a-zA-Z0-9_.]*)\\s*}}");

    private TemplateRenderer() {
    }

    public static Result render(String text, Map<String, String> tokenValues) {
        if (text == null || text.isEmpty()) return new Result(text == null ? "" : text, List.of());

        Matcher matcher = TOKEN_PATTERN.matcher(text);
        StringBuilder rendered = new StringBuilder();
        // LinkedHashSet rather than a plain List: the same {{token}} can appear more than once in
        // one template (e.g. a subject that repeats {{lead.companyname}}), and it should only be
        // reported once, in first-seen order - not once per occurrence.
        Set<String> unresolvedTokens = new LinkedHashSet<>();

        while (matcher.find()) {
            String tokenName = matcher.group(1).toLowerCase(Locale.ROOT);
            String value = tokenValues.get(tokenName);
            if (value != null) {
                matcher.appendReplacement(rendered, Matcher.quoteReplacement(value));
            } else {
                unresolvedTokens.add(matcher.group(0));
                matcher.appendReplacement(rendered, Matcher.quoteReplacement(matcher.group(0)));
            }
        }
        matcher.appendTail(rendered);

        return new Result(rendered.toString(), new ArrayList<>(unresolvedTokens));
    }

    public record Result(String text, List<String> unresolvedTokens) {
    }
}
