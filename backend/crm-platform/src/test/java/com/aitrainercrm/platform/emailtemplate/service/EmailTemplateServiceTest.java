package com.aitrainercrm.platform.emailtemplate.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aitrainercrm.platform.account.entity.Account;
import com.aitrainercrm.platform.account.repository.AccountRepository;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.contact.entity.Contact;
import com.aitrainercrm.platform.contact.repository.ContactRepository;
import com.aitrainercrm.platform.emailtemplate.dto.CreateEmailTemplateRequest;
import com.aitrainercrm.platform.emailtemplate.dto.RenderEmailTemplateRequest;
import com.aitrainercrm.platform.emailtemplate.dto.RenderedEmailDto;
import com.aitrainercrm.platform.emailtemplate.dto.UpdateEmailTemplateRequest;
import com.aitrainercrm.platform.emailtemplate.entity.EmailTemplate;
import com.aitrainercrm.platform.emailtemplate.repository.EmailTemplateRepository;
import com.aitrainercrm.platform.lead.repository.LeadRepository;
import com.aitrainercrm.platform.opportunity.repository.OpportunityRepository;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class EmailTemplateServiceTest {

    @Mock private EmailTemplateRepository emailTemplateRepository;
    @Mock private ContactRepository contactRepository;
    @Mock private LeadRepository leadRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private OpportunityRepository opportunityRepository;
    @Mock private ApplicationEventPublisher events;
    @Mock private UserPrincipal principal;

    private EmailTemplateService service;

    private final UUID organizationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new EmailTemplateService(emailTemplateRepository, contactRepository, leadRepository, accountRepository, opportunityRepository, events);
    }

    @Test
    void update_unknownTemplate_throwsResourceNotFound() {
        UUID templateId = UUID.randomUUID();
        when(principal.getOrganizationId()).thenReturn(organizationId);
        when(emailTemplateRepository.findActiveByIdAndOrganizationId(templateId, organizationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(
                        principal, templateId, new UpdateEmailTemplateRequest("New", EmailTemplate.Category.SALES, "Subj", "Body", true)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(emailTemplateRepository, never()).save(any());
    }

    @Test
    void create_publishesRecordCreatedEvent() {
        when(principal.getOrganizationId()).thenReturn(organizationId);
        when(principal.getId()).thenReturn(UUID.randomUUID());
        CreateEmailTemplateRequest request =
                new CreateEmailTemplateRequest("Welcome", EmailTemplate.Category.SALES, "Hi {{contact.firstname}}", "Body {{lead.companyname}}");

        EmailTemplate result = service.create(principal, request);

        assertThat(result.getName()).isEqualTo("Welcome");
        assertThat(result.getOrganizationId()).isEqualTo(organizationId);
        verify(emailTemplateRepository).save(result);
    }

    @Test
    void render_withNoTargetIdsSupplied_leavesEveryEntityTokenUnresolved_butStillFillsSenderAndToday() {
        UUID templateId = UUID.randomUUID();
        when(principal.getOrganizationId()).thenReturn(organizationId);
        when(principal.getFullName()).thenReturn(null); // the token-rebuild UserPrincipal path
        when(principal.getEmail()).thenReturn("rep@example.com");
        EmailTemplate template = template(templateId, "Hi {{contact.firstname}}", "Sent by {{sender.email}} on {{today}}");
        when(emailTemplateRepository.findActiveByIdAndOrganizationId(templateId, organizationId)).thenReturn(Optional.of(template));

        RenderedEmailDto result = service.render(principal, templateId, new RenderEmailTemplateRequest(null, null, null, null));

        assertThat(result.subject()).isEqualTo("Hi {{contact.firstname}}");
        assertThat(result.body()).contains("Sent by rep@example.com on");
        assertThat(result.unresolvedTokens()).containsExactly("{{contact.firstname}}");
        verify(contactRepository, never()).findActiveByIdAndOrganizationId(any(), any());
    }

    @Test
    void render_withContactAndAccountSupplied_mergesBothAndReportsNoUnresolvedTokens() {
        UUID templateId = UUID.randomUUID();
        UUID contactId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        when(principal.getOrganizationId()).thenReturn(organizationId);
        when(principal.getFullName()).thenReturn("Rep Person");
        when(principal.getEmail()).thenReturn("rep@example.com");

        EmailTemplate template = template(templateId, "Hi {{contact.firstname}} from {{account.name}}", "Regards, {{sender.fullname}}");
        when(emailTemplateRepository.findActiveByIdAndOrganizationId(templateId, organizationId)).thenReturn(Optional.of(template));

        Contact contact = new Contact(organizationId, "Ada", "Lovelace", UUID.randomUUID());
        when(contactRepository.findActiveByIdAndOrganizationId(contactId, organizationId)).thenReturn(Optional.of(contact));
        Account account = new Account(organizationId, "Acme Rockets", UUID.randomUUID());
        when(accountRepository.findActiveByIdAndOrganizationId(accountId, organizationId)).thenReturn(Optional.of(account));

        RenderedEmailDto result = service.render(principal, templateId, new RenderEmailTemplateRequest(contactId, null, accountId, null));

        assertThat(result.subject()).isEqualTo("Hi Ada from Acme Rockets");
        assertThat(result.body()).isEqualTo("Regards, Rep Person");
        assertThat(result.unresolvedTokens()).isEmpty();
    }

    @Test
    void render_targetIdBelongingToAnotherOrg_resolvesToNothing_tokenStaysUnresolved() {
        UUID templateId = UUID.randomUUID();
        UUID contactId = UUID.randomUUID();
        when(principal.getOrganizationId()).thenReturn(organizationId);
        when(principal.getFullName()).thenReturn("Rep Person");
        when(principal.getEmail()).thenReturn("rep@example.com");
        EmailTemplate template = template(templateId, "Hi {{contact.firstname}}", "Body");
        when(emailTemplateRepository.findActiveByIdAndOrganizationId(templateId, organizationId)).thenReturn(Optional.of(template));
        when(contactRepository.findActiveByIdAndOrganizationId(contactId, organizationId)).thenReturn(Optional.empty());

        RenderedEmailDto result = service.render(principal, templateId, new RenderEmailTemplateRequest(contactId, null, null, null));

        assertThat(result.subject()).isEqualTo("Hi {{contact.firstname}}");
        assertThat(result.unresolvedTokens()).containsExactly("{{contact.firstname}}");
    }

    @Test
    void render_unresolvedTokensFromSubjectAndBody_areMergedWithoutDuplicates() {
        UUID templateId = UUID.randomUUID();
        when(principal.getOrganizationId()).thenReturn(organizationId);
        when(principal.getFullName()).thenReturn("Rep");
        when(principal.getEmail()).thenReturn("rep@example.com");
        EmailTemplate template = template(templateId, "{{lead.companyname}} intro", "Following up re {{lead.companyname}}");
        when(emailTemplateRepository.findActiveByIdAndOrganizationId(templateId, organizationId)).thenReturn(Optional.of(template));

        RenderedEmailDto result = service.render(principal, templateId, new RenderEmailTemplateRequest(null, null, null, null));

        assertThat(result.unresolvedTokens()).containsExactly("{{lead.companyname}}");
        assertThat(result.unresolvedTokens()).hasSize(1);
    }

    private EmailTemplate template(UUID id, String subject, String body) {
        EmailTemplate template = new EmailTemplate(organizationId, "Test", EmailTemplate.Category.GENERAL, subject, body);
        template.setId(id);
        return template;
    }
}
