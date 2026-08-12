package com.aitrainercrm.platform.dedupe.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aitrainercrm.platform.account.entity.Account;
import com.aitrainercrm.platform.account.repository.AccountRepository;
import com.aitrainercrm.platform.activity.entity.Activity;
import com.aitrainercrm.platform.activity.repository.ActivityRepository;
import com.aitrainercrm.platform.attachment.entity.Attachment;
import com.aitrainercrm.platform.attachment.repository.AttachmentRepository;
import com.aitrainercrm.platform.calendar.entity.CalendarEvent;
import com.aitrainercrm.platform.calendar.repository.CalendarEventRepository;
import com.aitrainercrm.platform.common.exception.BusinessException;
import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.contact.repository.ContactRepository;
import com.aitrainercrm.platform.dedupe.dto.DuplicateMatchDto;
import com.aitrainercrm.platform.dedupe.entity.DuplicateMatch;
import com.aitrainercrm.platform.dedupe.repository.DuplicateMatchRepository;
import com.aitrainercrm.platform.email.entity.EmailMessage;
import com.aitrainercrm.platform.email.repository.EmailMessageRepository;
import com.aitrainercrm.platform.lead.entity.Lead;
import com.aitrainercrm.platform.lead.repository.LeadRepository;
import com.aitrainercrm.platform.opportunity.repository.OpportunityRepository;
import com.aitrainercrm.platform.role.entity.Permission;
import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DuplicateMatchServiceTest {

    @Mock private DuplicateMatchRepository duplicateMatchRepository;
    @Mock private LeadRepository leadRepository;
    @Mock private ContactRepository contactRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private ActivityRepository activityRepository;
    @Mock private AttachmentRepository attachmentRepository;
    @Mock private EmailMessageRepository emailMessageRepository;
    @Mock private CalendarEventRepository calendarEventRepository;
    @Mock private OpportunityRepository opportunityRepository;
    @Mock private ScopeAuthorizationService scopeAuthorizationService;
    @Mock private UserPrincipal principal;

    private DuplicateMatchService service;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new DuplicateMatchService(
                duplicateMatchRepository, leadRepository, contactRepository, accountRepository, activityRepository,
                attachmentRepository, emailMessageRepository, calendarEventRepository, opportunityRepository,
                scopeAuthorizationService, (event) -> { });
    }

    @Test
    void merge_survivorNotInThePair_returns400BeforeTouchingAnything() {
        UUID recordA = UUID.randomUUID();
        UUID recordB = UUID.randomUUID();
        DuplicateMatch match = pendingMatch(DuplicateMatch.EntityType.LEAD, recordA, recordB);
        when(duplicateMatchRepository.findByIdAndOrganizationId(match.getId(), organizationId)).thenReturn(Optional.of(match));
        when(principal.getOrganizationId()).thenReturn(organizationId);

        assertThatThrownBy(() -> service.merge(principal, match.getId(), UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("survivorId must be one of");
        verify(scopeAuthorizationService, never()).assertCanAccess(any(), any(), any(), any());
    }

    @Test
    void merge_matchAlreadyResolved_returns409() {
        DuplicateMatch match = pendingMatch(DuplicateMatch.EntityType.LEAD, UUID.randomUUID(), UUID.randomUUID());
        match.resolveDismissed(actorId);
        when(duplicateMatchRepository.findByIdAndOrganizationId(match.getId(), organizationId)).thenReturn(Optional.of(match));
        when(principal.getOrganizationId()).thenReturn(organizationId);

        assertThatThrownBy(() -> service.merge(principal, match.getId(), match.getRecordAId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already been dismissed");
    }

    @Test
    void merge_callerLacksUpdateOnOneRecord_throwsForbiddenAndReassignsNothing() {
        Lead recordA = activeLead(Lead.Status.NEW);
        Lead recordB = activeLead(Lead.Status.NEW);
        DuplicateMatch match = pendingMatch(DuplicateMatch.EntityType.LEAD, recordA.getId(), recordB.getId());
        when(duplicateMatchRepository.findByIdAndOrganizationId(match.getId(), organizationId)).thenReturn(Optional.of(match));
        when(principal.getOrganizationId()).thenReturn(organizationId);
        when(leadRepository.findActiveByIdAndOrganizationId(recordA.getId(), organizationId)).thenReturn(Optional.of(recordA));
        when(leadRepository.findActiveByIdAndOrganizationId(recordB.getId(), organizationId)).thenReturn(Optional.of(recordB));
        org.mockito.Mockito.doThrow(new ForbiddenException("nope"))
                .when(scopeAuthorizationService)
                .assertCanAccess(principal, Permission.Resource.LEAD, Permission.Action.UPDATE, recordB.getOwnerId());

        assertThatThrownBy(() -> service.merge(principal, match.getId(), recordA.getId())).isInstanceOf(ForbiddenException.class);

        verify(activityRepository, never()).reassignRelatedTo(any(), any(), any(), any());
        verify(leadRepository, never()).save(any());
    }

    @Test
    void merge_eitherLeadAlreadyConverted_returns409() {
        Lead recordA = activeLead(Lead.Status.CONVERTED);
        Lead recordB = activeLead(Lead.Status.NEW);
        DuplicateMatch match = pendingMatch(DuplicateMatch.EntityType.LEAD, recordA.getId(), recordB.getId());
        stubAccessible(match);
        when(leadRepository.findActiveByIdAndOrganizationId(recordA.getId(), organizationId)).thenReturn(Optional.of(recordA));
        when(leadRepository.findActiveByIdAndOrganizationId(recordB.getId(), organizationId)).thenReturn(Optional.of(recordB));

        assertThatThrownBy(() -> service.merge(principal, match.getId(), recordB.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already converted");
    }

    @Test
    void merge_leadPair_reassignsGenericRelatedToAndSoftDeletesTheAbsorbedLead() {
        Lead recordA = activeLead(Lead.Status.NEW);
        Lead recordB = activeLead(Lead.Status.NEW);
        DuplicateMatch match = pendingMatch(DuplicateMatch.EntityType.LEAD, recordA.getId(), recordB.getId());
        stubAccessible(match);
        when(leadRepository.findActiveByIdAndOrganizationId(recordA.getId(), organizationId)).thenReturn(Optional.of(recordA));
        when(leadRepository.findActiveByIdAndOrganizationId(recordB.getId(), organizationId)).thenReturn(Optional.of(recordB));

        DuplicateMatchDto result = service.merge(principal, match.getId(), recordA.getId());

        assertThat(result.status()).isEqualTo(DuplicateMatch.Status.MERGED);
        assertThat(result.survivorId()).isEqualTo(recordA.getId());
        assertThat(result.absorbedId()).isEqualTo(recordB.getId());
        verify(activityRepository).reassignRelatedTo(organizationId, Activity.RelatedToType.LEAD, recordB.getId(), recordA.getId());
        verify(attachmentRepository).reassignRelatedTo(organizationId, Attachment.RelatedToType.LEAD, recordB.getId(), recordA.getId());
        verify(emailMessageRepository).reassignRelatedTo(organizationId, EmailMessage.RelatedToType.LEAD, recordB.getId(), recordA.getId());
        verify(calendarEventRepository).reassignRelatedTo(organizationId, CalendarEvent.RelatedToType.LEAD, recordB.getId(), recordA.getId());
        verify(leadRepository).save(recordB);
        assertThat(recordB.getDeletedAt()).isNotNull();
        verify(duplicateMatchRepository).save(match);
    }

    @Test
    void merge_accountPair_alsoReassignsContactsAndOpportunities() {
        Account recordA = activeAccount();
        Account recordB = activeAccount();
        DuplicateMatch match = pendingMatch(DuplicateMatch.EntityType.ACCOUNT, recordA.getId(), recordB.getId());
        stubAccessible(match);
        when(accountRepository.findActiveByIdAndOrganizationId(recordA.getId(), organizationId)).thenReturn(Optional.of(recordA));
        when(accountRepository.findActiveByIdAndOrganizationId(recordB.getId(), organizationId)).thenReturn(Optional.of(recordB));

        service.merge(principal, match.getId(), recordA.getId());

        verify(contactRepository).reassignAccountId(organizationId, recordB.getId(), recordA.getId());
        verify(opportunityRepository).reassignAccountId(organizationId, recordB.getId(), recordA.getId());
        verify(opportunityRepository, never()).reassignPrimaryContactId(any(), any(), any());
        verify(accountRepository).save(recordB);
    }

    @Test
    void dismiss_marksDismissedWithoutTouchingAnyChildRecords() {
        Lead recordA = activeLead(Lead.Status.NEW);
        Lead recordB = activeLead(Lead.Status.NEW);
        DuplicateMatch match = pendingMatch(DuplicateMatch.EntityType.LEAD, recordA.getId(), recordB.getId());
        stubAccessible(match);
        when(leadRepository.findActiveByIdAndOrganizationId(recordA.getId(), organizationId)).thenReturn(Optional.of(recordA));
        when(leadRepository.findActiveByIdAndOrganizationId(recordB.getId(), organizationId)).thenReturn(Optional.of(recordB));

        DuplicateMatchDto result = service.dismiss(principal, match.getId());

        assertThat(result.status()).isEqualTo(DuplicateMatch.Status.DISMISSED);
        verify(activityRepository, never()).reassignRelatedTo(any(), any(), any(), any());
        verify(leadRepository, never()).save(any());
    }

    @Test
    void list_filtersOutPairsWhereEitherOwnerIsOutsideTheCallersVisibility() {
        Lead visibleA = activeLead(Lead.Status.NEW);
        Lead visibleB = activeLead(Lead.Status.NEW);
        Lead hiddenOwnerLead = activeLead(Lead.Status.NEW);
        DuplicateMatch visiblePair = pendingMatch(DuplicateMatch.EntityType.LEAD, visibleA.getId(), visibleB.getId());
        DuplicateMatch hiddenPair = pendingMatch(DuplicateMatch.EntityType.LEAD, visibleA.getId(), hiddenOwnerLead.getId());

        when(principal.getOrganizationId()).thenReturn(organizationId);
        when(scopeAuthorizationService.visibleOwnerIds(principal, Permission.Resource.LEAD, Permission.Action.READ))
                .thenReturn(Optional.of(Set.of(visibleA.getOwnerId(), visibleB.getOwnerId())));
        when(duplicateMatchRepository.findByOrganizationIdAndEntityTypeAndStatusOrderByCreatedAtAsc(
                        organizationId, DuplicateMatch.EntityType.LEAD, DuplicateMatch.Status.PENDING))
                .thenReturn(List.of(visiblePair, hiddenPair));
        when(leadRepository.findActiveByIdAndOrganizationId(visibleA.getId(), organizationId)).thenReturn(Optional.of(visibleA));
        when(leadRepository.findActiveByIdAndOrganizationId(visibleB.getId(), organizationId)).thenReturn(Optional.of(visibleB));
        when(leadRepository.findActiveByIdAndOrganizationId(hiddenOwnerLead.getId(), organizationId)).thenReturn(Optional.of(hiddenOwnerLead));

        List<DuplicateMatchDto> result = service.list(principal, DuplicateMatch.EntityType.LEAD, DuplicateMatch.Status.PENDING);

        assertThat(result).extracting(DuplicateMatchDto::id).containsExactly(visiblePair.getId());
    }

    /** assertCanAccess is void and permissive by default under Mockito (a mocked void method no-ops unless stubbed to throw), so "the caller is allowed" needs no stub of its own - only the match lookup and organizationId do. */
    private void stubAccessible(DuplicateMatch match) {
        when(duplicateMatchRepository.findByIdAndOrganizationId(match.getId(), organizationId)).thenReturn(Optional.of(match));
        when(principal.getOrganizationId()).thenReturn(organizationId);
    }

    private DuplicateMatch pendingMatch(DuplicateMatch.EntityType entityType, UUID recordAId, UUID recordBId) {
        DuplicateMatch match = new DuplicateMatch(organizationId, entityType, recordAId, recordBId, DuplicateMatch.MatchReason.EMAIL);
        match.setId(UUID.randomUUID());
        return match;
    }

    private Lead activeLead(Lead.Status status) {
        Lead lead = new Lead(organizationId, "First", "Last", UUID.randomUUID());
        lead.setId(UUID.randomUUID());
        lead.setStatus(status);
        return lead;
    }

    private Account activeAccount() {
        Account account = new Account(organizationId, "Acme", UUID.randomUUID());
        account.setId(UUID.randomUUID());
        return account;
    }
}
