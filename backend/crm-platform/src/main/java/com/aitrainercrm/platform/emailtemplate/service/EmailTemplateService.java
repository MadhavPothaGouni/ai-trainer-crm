package com.aitrainercrm.platform.emailtemplate.service;

import com.aitrainercrm.platform.account.repository.AccountRepository;
import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.contact.repository.ContactRepository;
import com.aitrainercrm.platform.emailtemplate.dto.CreateEmailTemplateRequest;
import com.aitrainercrm.platform.emailtemplate.dto.RenderEmailTemplateRequest;
import com.aitrainercrm.platform.emailtemplate.dto.RenderedEmailDto;
import com.aitrainercrm.platform.emailtemplate.dto.UpdateEmailTemplateRequest;
import com.aitrainercrm.platform.emailtemplate.entity.EmailTemplate;
import com.aitrainercrm.platform.emailtemplate.render.TemplateRenderer;
import com.aitrainercrm.platform.emailtemplate.repository.EmailTemplateRepository;
import com.aitrainercrm.platform.lead.repository.LeadRepository;
import com.aitrainercrm.platform.opportunity.repository.OpportunityRepository;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The template catalog, plus {@link #render}, the mail-merge entry point. Like {@link
 * com.aitrainercrm.platform.product.service.ProductService}, no {@link
 * com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService} call anywhere - see
 * V27's migration comment and {@link EmailTemplate}'s javadoc for why EMAIL_TEMPLATE's three seeded
 * scopes (TEAM/DEPARTMENT/ORGANIZATION, no OWN) are the entire authorization story.
 *
 * <p>{@link #render} deliberately does NOT run the target ids it's given (contactId/leadId/
 * accountId/opportunityId) through each entity's own ScopeAuthorizationService check the way, say,
 * ActivityService would before attaching an Activity to one of them - a template render is a
 * read-only, ephemeral preview (nothing is persisted, no record is modified), and every id it
 * accepts is still resolved with a real organization-scoped lookup ({@code
 * findActiveByIdAndOrganizationId} on each repository), so a caller can never merge data belonging
 * to a different tenant, only records outside their own OWN/TEAM/DEPARTMENT slice within their own
 * org - the same trust boundary REPORT:READ already extends across the whole org for aggregate
 * numbers. An id that doesn't resolve (wrong org, wrong type, already deleted) is silently skipped
 * rather than rejecting the whole render, so a stale id in an old draft doesn't block sending an
 * email that doesn't even use that entity's tokens.
 */
@Service
@RequiredArgsConstructor
public class EmailTemplateService {

    private final EmailTemplateRepository emailTemplateRepository;
    private final ContactRepository contactRepository;
    private final LeadRepository leadRepository;
    private final AccountRepository accountRepository;
    private final OpportunityRepository opportunityRepository;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<EmailTemplate> list(UserPrincipal principal, EmailTemplate.Category category, Pageable pageable) {
        return category == null
                ? emailTemplateRepository.findByOrganizationIdAndDeletedAtIsNull(principal.getOrganizationId(), pageable)
                : emailTemplateRepository.findByOrganizationIdAndCategoryAndDeletedAtIsNull(
                        principal.getOrganizationId(), category, pageable);
    }

    @Transactional(readOnly = true)
    public EmailTemplate get(UserPrincipal principal, UUID templateId) {
        return findOrThrow(principal.getOrganizationId(), templateId);
    }

    @Transactional
    public EmailTemplate create(UserPrincipal principal, CreateEmailTemplateRequest request) {
        EmailTemplate template = new EmailTemplate(
                principal.getOrganizationId(), request.name(), request.category(), request.subject(), request.body());
        emailTemplateRepository.save(template);

        events.publishEvent(
                new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "EmailTemplate", template.getId()));
        return template;
    }

    @Transactional
    public EmailTemplate update(UserPrincipal principal, UUID templateId, UpdateEmailTemplateRequest request) {
        EmailTemplate template = findOrThrow(principal.getOrganizationId(), templateId);
        template.setName(request.name());
        template.setCategory(request.category());
        template.setSubject(request.subject());
        template.setBody(request.body());
        template.setActive(request.active());
        emailTemplateRepository.save(template);

        events.publishEvent(
                new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "EmailTemplate", template.getId()));
        return template;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID templateId) {
        EmailTemplate template = findOrThrow(principal.getOrganizationId(), templateId);
        template.setDeletedAt(Instant.now());
        emailTemplateRepository.save(template);

        events.publishEvent(
                new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "EmailTemplate", templateId));
    }

    @Transactional(readOnly = true)
    public RenderedEmailDto render(UserPrincipal principal, UUID templateId, RenderEmailTemplateRequest request) {
        EmailTemplate template = findOrThrow(principal.getOrganizationId(), templateId);
        Map<String, String> tokenValues = buildTokenValues(principal, request);

        TemplateRenderer.Result subjectResult = TemplateRenderer.render(template.getSubject(), tokenValues);
        TemplateRenderer.Result bodyResult = TemplateRenderer.render(template.getBody(), tokenValues);

        List<String> unresolvedTokens = new ArrayList<>(subjectResult.unresolvedTokens());
        for (String token : bodyResult.unresolvedTokens()) {
            if (!unresolvedTokens.contains(token)) unresolvedTokens.add(token);
        }
        return new RenderedEmailDto(subjectResult.text(), bodyResult.text(), unresolvedTokens);
    }

    private Map<String, String> buildTokenValues(UserPrincipal principal, RenderEmailTemplateRequest request) {
        Map<String, String> tokenValues = new HashMap<>();
        tokenValues.put("today", LocalDate.now().toString());
        // principal.getFullName() is only populated when UserPrincipal was built from a real User
        // row (the register/login path); the token-rebuild constructor JwtTokenProvider uses on
        // every subsequent authenticated request leaves it null - nullToEmpty here (rather than
        // letting a null reach Matcher.quoteReplacement, which throws) is what keeps {{sender.*}}
        // working the same way regardless of which UserPrincipal constructor built this request's
        // principal.
        tokenValues.put("sender.fullname", nullToEmpty(principal.getFullName()));
        tokenValues.put("sender.email", nullToEmpty(principal.getEmail()));

        UUID organizationId = principal.getOrganizationId();

        if (request.contactId() != null) {
            contactRepository.findActiveByIdAndOrganizationId(request.contactId(), organizationId).ifPresent(contact -> {
                tokenValues.put("contact.firstname", nullToEmpty(contact.getFirstName()));
                tokenValues.put("contact.lastname", nullToEmpty(contact.getLastName()));
                tokenValues.put("contact.fullname", nullToEmpty(contact.getFullName()));
                tokenValues.put("contact.email", nullToEmpty(contact.getEmail()));
                tokenValues.put("contact.title", nullToEmpty(contact.getTitle()));
            });
        }
        if (request.leadId() != null) {
            leadRepository.findActiveByIdAndOrganizationId(request.leadId(), organizationId).ifPresent(lead -> {
                tokenValues.put("lead.firstname", nullToEmpty(lead.getFirstName()));
                tokenValues.put("lead.lastname", nullToEmpty(lead.getLastName()));
                tokenValues.put("lead.fullname", nullToEmpty(lead.getFullName()));
                tokenValues.put("lead.companyname", nullToEmpty(lead.getCompanyName()));
                tokenValues.put("lead.email", nullToEmpty(lead.getEmail()));
            });
        }
        if (request.accountId() != null) {
            accountRepository.findActiveByIdAndOrganizationId(request.accountId(), organizationId).ifPresent(account -> {
                tokenValues.put("account.name", nullToEmpty(account.getName()));
                tokenValues.put("account.industry", nullToEmpty(account.getIndustry()));
                tokenValues.put("account.phone", nullToEmpty(account.getPhone()));
            });
        }
        if (request.opportunityId() != null) {
            opportunityRepository.findActiveByIdAndOrganizationId(request.opportunityId(), organizationId).ifPresent(opportunity -> {
                tokenValues.put("opportunity.name", nullToEmpty(opportunity.getName()));
                tokenValues.put("opportunity.amount", opportunity.getAmount() == null ? "" : opportunity.getAmount().toPlainString());
                tokenValues.put("opportunity.stage", opportunity.getStage().name());
            });
        }
        return tokenValues;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private EmailTemplate findOrThrow(UUID organizationId, UUID templateId) {
        return emailTemplateRepository.findActiveByIdAndOrganizationId(templateId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("EmailTemplate", templateId));
    }
}
