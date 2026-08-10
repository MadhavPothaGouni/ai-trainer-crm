package com.aitrainercrm.platform.lead.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aitrainercrm.platform.account.entity.Account;
import com.aitrainercrm.platform.account.repository.AccountRepository;
import com.aitrainercrm.platform.common.exception.BusinessException;
import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.contact.entity.Contact;
import com.aitrainercrm.platform.contact.repository.ContactRepository;
import com.aitrainercrm.platform.lead.dto.ConvertLeadRequest;
import com.aitrainercrm.platform.lead.dto.LeadConversionResult;
import com.aitrainercrm.platform.lead.entity.Lead;
import com.aitrainercrm.platform.lead.repository.LeadRepository;
import com.aitrainercrm.platform.opportunity.entity.Opportunity;
import com.aitrainercrm.platform.opportunity.repository.OpportunityRepository;
import com.aitrainercrm.platform.role.entity.Permission;
import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * LeadService#convert is the one piece of business logic in the CRM domain
 * that writes to four different tables in a single call - these tests
 * exist to pin down exactly what gets created (and, just as importantly,
 * what doesn't) under each combination of the request's optional fields.
 */
@ExtendWith(MockitoExtension.class)
class LeadServiceTest {

    @Mock private LeadRepository leadRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private ContactRepository contactRepository;
    @Mock private OpportunityRepository opportunityRepository;
    @Mock private UserRepository userRepository;
    @Mock private ScopeAuthorizationService scopeAuthorizationService;
    @Mock private ApplicationEventPublisher events;

    private LeadService leadService;
    private UUID organizationId;
    private Lead lead;
    private UserPrincipal principal;

    @BeforeEach
    void setUp() {
        leadService = new LeadService(
                leadRepository, accountRepository, contactRepository, opportunityRepository, userRepository,
                scopeAuthorizationService, events);

        organizationId = UUID.randomUUID();
        UUID leadOwnerId = UUID.randomUUID();
        principal = new UserPrincipal(leadOwnerId, "rep@example.com", organizationId, List.of("LEAD:UPDATE:OWN"));

        lead = new Lead(organizationId, "Grace", "Hopper", leadOwnerId);
        lead.setId(UUID.randomUUID());
        lead.setCompanyName("Acme Corp");
        lead.setEmail("grace@acme.example.com");

        when(leadRepository.findActiveByIdAndOrganizationId(lead.getId(), organizationId)).thenReturn(Optional.of(lead));
        // lenient(): not every test exercises every resource's CREATE check (e.g. linking an
        // existing account skips the ACCOUNT check entirely, and one test overrides just the
        // ACCOUNT case to NONE) - this default just needs to be available, not necessarily used,
        // in every single test.
        org.mockito.Mockito.lenient()
                .when(scopeAuthorizationService.highestGranted(eq(principal), any(), eq(Permission.Action.CREATE)))
                .thenReturn(ScopeAuthorizationService.Access.ORGANIZATION);
    }

    @Test
    void convert_withNoExistingAccountAndDefaultRequest_createsAccountContactAndOpportunity() {
        stubSavesReturnSameEntityWithGeneratedId();
        ConvertLeadRequest request = new ConvertLeadRequest(null, null, null, null, null, null);

        LeadConversionResult result = leadService.convert(principal, lead.getId(), request);

        assertThat(result.leadId()).isEqualTo(lead.getId());
        assertThat(result.accountId()).isNotNull();
        assertThat(result.contactId()).isNotNull();
        assertThat(result.opportunityId()).isNotNull();

        assertThat(lead.getStatus()).isEqualTo(Lead.Status.CONVERTED);
        assertThat(lead.getConvertedAt()).isNotNull();
        assertThat(lead.getConvertedAccountId()).isEqualTo(result.accountId());
        assertThat(lead.getConvertedContactId()).isEqualTo(result.contactId());
        assertThat(lead.getConvertedOpportunityId()).isEqualTo(result.opportunityId());

        verify(accountRepository).save(any(Account.class));
        verify(contactRepository).save(any(Contact.class));
        verify(opportunityRepository).save(any(Opportunity.class));
    }

    @Test
    void convert_newAccountUsesLeadsCompanyNameWhenNoOverrideGiven() {
        stubSavesReturnSameEntityWithGeneratedId();
        ConvertLeadRequest request = new ConvertLeadRequest(null, null, null, null, null, null);

        leadService.convert(principal, lead.getId(), request);

        var accountCaptor = org.mockito.ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(accountCaptor.capture());
        assertThat(accountCaptor.getValue().getName()).isEqualTo("Acme Corp");
    }

    @Test
    void convert_withExistingAccountId_linksInsteadOfCreatingANewOne() {
        UUID existingAccountId = UUID.randomUUID();
        when(accountRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(existingAccountId, organizationId)).thenReturn(true);
        stubContactSaveReturnsGeneratedId();
        stubOpportunitySaveReturnsGeneratedId();
        ConvertLeadRequest request = new ConvertLeadRequest(existingAccountId, null, null, null, null, null);

        LeadConversionResult result = leadService.convert(principal, lead.getId(), request);

        assertThat(result.accountId()).isEqualTo(existingAccountId);
        verify(accountRepository, never()).save(any());
    }

    @Test
    void convert_withCreateOpportunityFalse_skipsOpportunityEntirely() {
        stubAccountSaveReturnsGeneratedId();
        stubContactSaveReturnsGeneratedId();
        ConvertLeadRequest request = new ConvertLeadRequest(null, null, false, null, null, null);

        LeadConversionResult result = leadService.convert(principal, lead.getId(), request);

        assertThat(result.opportunityId()).isNull();
        assertThat(lead.getConvertedOpportunityId()).isNull();
        verify(opportunityRepository, never()).save(any());
    }

    @Test
    void convert_onAnAlreadyConvertedLead_throwsRatherThanConvertingAgain() {
        lead.setStatus(Lead.Status.CONVERTED);
        lead.setConvertedAt(Instant.now());
        ConvertLeadRequest request = new ConvertLeadRequest(null, null, null, null, null, null);

        assertThatThrownBy(() -> leadService.convert(principal, lead.getId(), request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already been converted");

        verify(accountRepository, never()).save(any());
    }

    @Test
    void convert_whenCallerCannotCreateAccounts_isRejectedBeforeAnythingIsWritten() {
        when(scopeAuthorizationService.highestGranted(eq(principal), eq(Permission.Resource.ACCOUNT), eq(Permission.Action.CREATE)))
                .thenReturn(ScopeAuthorizationService.Access.NONE);
        ConvertLeadRequest request = new ConvertLeadRequest(null, null, null, null, null, null);

        assertThatThrownBy(() -> leadService.convert(principal, lead.getId(), request))
                .isInstanceOf(ForbiddenException.class);

        verify(accountRepository, never()).save(any());
        verify(contactRepository, never()).save(any());
        verify(leadRepository, never()).save(any());
    }

    private void stubAccountSaveReturnsGeneratedId() {
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account account = invocation.getArgument(0);
            account.setId(UUID.randomUUID());
            return account;
        });
    }

    private void stubContactSaveReturnsGeneratedId() {
        when(contactRepository.save(any(Contact.class))).thenAnswer(invocation -> {
            Contact contact = invocation.getArgument(0);
            contact.setId(UUID.randomUUID());
            return contact;
        });
    }

    private void stubOpportunitySaveReturnsGeneratedId() {
        when(opportunityRepository.save(any(Opportunity.class))).thenAnswer(invocation -> {
            Opportunity opportunity = invocation.getArgument(0);
            opportunity.setId(UUID.randomUUID());
            return opportunity;
        });
    }

    private void stubSavesReturnSameEntityWithGeneratedId() {
        stubAccountSaveReturnsGeneratedId();
        stubContactSaveReturnsGeneratedId();
        stubOpportunitySaveReturnsGeneratedId();
    }
}
