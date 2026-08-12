package com.aitrainercrm.platform.dedupe.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aitrainercrm.platform.account.entity.Account;
import com.aitrainercrm.platform.account.repository.AccountRepository;
import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.contact.entity.Contact;
import com.aitrainercrm.platform.contact.repository.ContactRepository;
import com.aitrainercrm.platform.dedupe.entity.DuplicateMatch;
import com.aitrainercrm.platform.dedupe.repository.DuplicateMatchRepository;
import com.aitrainercrm.platform.lead.entity.Lead;
import com.aitrainercrm.platform.lead.repository.LeadRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Every dependency mocked - the listener invoked directly rather than through the event bus, same reasoning TerritoryAssignmentListenerTest documents. */
@ExtendWith(MockitoExtension.class)
class DuplicateDetectionListenerTest {

    @Mock private LeadRepository leadRepository;
    @Mock private ContactRepository contactRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private DuplicateMatchRepository duplicateMatchRepository;

    private DuplicateDetectionListener listener;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID actorUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        listener = new DuplicateDetectionListener(leadRepository, contactRepository, accountRepository, duplicateMatchRepository);
    }

    @Test
    void onRecordCreated_unrecognizedResourceType_touchesNothing() {
        listener.onRecordCreated(new CrmAuditEvents.RecordCreated(actorUserId, organizationId, "Opportunity", UUID.randomUUID()));

        verify(leadRepository, never()).findActiveByIdAndOrganizationId(any(), any());
        verify(contactRepository, never()).findActiveByIdAndOrganizationId(any(), any());
        verify(accountRepository, never()).findActiveByIdAndOrganizationId(any(), any());
    }

    @Test
    void onRecordCreated_leadWithEmail_matchesByEmailAndFlagsThePair() {
        Instant earlier = Instant.now().minus(1, ChronoUnit.DAYS);
        Lead newLead = lead("ada@example.com", null, earlier.plus(1, ChronoUnit.DAYS));
        Lead existingLead = lead("ada@example.com", null, earlier);
        when(leadRepository.findActiveByIdAndOrganizationId(newLead.getId(), organizationId)).thenReturn(Optional.of(newLead));
        when(leadRepository.findDuplicateCandidatesByEmail(organizationId, "ada@example.com", newLead.getId(), Lead.Status.CONVERTED))
                .thenReturn(List.of(existingLead));
        when(duplicateMatchRepository.findByOrganizationIdAndEntityTypeAndRecordAIdAndRecordBId(
                        organizationId, DuplicateMatch.EntityType.LEAD, existingLead.getId(), newLead.getId()))
                .thenReturn(Optional.empty());

        listener.onRecordCreated(new CrmAuditEvents.RecordCreated(actorUserId, organizationId, "Lead", newLead.getId()));

        ArgumentCaptor<DuplicateMatch> captor = ArgumentCaptor.forClass(DuplicateMatch.class);
        verify(duplicateMatchRepository).save(captor.capture());
        DuplicateMatch saved = captor.getValue();
        assertThat(saved.getEntityType()).isEqualTo(DuplicateMatch.EntityType.LEAD);
        assertThat(saved.getMatchReason()).isEqualTo(DuplicateMatch.MatchReason.EMAIL);
        // existingLead was created earlier, so it's recordA - the normalized, order-independent pair.
        assertThat(saved.getRecordAId()).isEqualTo(existingLead.getId());
        assertThat(saved.getRecordBId()).isEqualTo(newLead.getId());
    }

    @Test
    void onRecordCreated_pairAlreadyFlagged_doesNotSaveASecondRow() {
        Lead newLead = lead("ada@example.com", null, Instant.now());
        Lead existingLead = lead("ada@example.com", null, Instant.now().minusSeconds(60));
        when(leadRepository.findActiveByIdAndOrganizationId(newLead.getId(), organizationId)).thenReturn(Optional.of(newLead));
        when(leadRepository.findDuplicateCandidatesByEmail(organizationId, "ada@example.com", newLead.getId(), Lead.Status.CONVERTED))
                .thenReturn(List.of(existingLead));
        when(duplicateMatchRepository.findByOrganizationIdAndEntityTypeAndRecordAIdAndRecordBId(
                        organizationId, DuplicateMatch.EntityType.LEAD, existingLead.getId(), newLead.getId()))
                .thenReturn(Optional.of(new DuplicateMatch(
                        organizationId, DuplicateMatch.EntityType.LEAD, existingLead.getId(), newLead.getId(), DuplicateMatch.MatchReason.EMAIL)));

        listener.onRecordCreated(new CrmAuditEvents.RecordCreated(actorUserId, organizationId, "Lead", newLead.getId()));

        verify(duplicateMatchRepository, never()).save(any());
    }

    @Test
    void onRecordCreated_leadNoEmail_fallsBackToNameAndCompanyMatch() {
        Lead newLead = lead(null, "Analytical Engines", Instant.now());
        newLead.setFirstName("Ada");
        newLead.setLastName("Lovelace");
        Lead existingLead = lead(null, "Analytical Engines", Instant.now().minusSeconds(60));
        when(leadRepository.findActiveByIdAndOrganizationId(newLead.getId(), organizationId)).thenReturn(Optional.of(newLead));
        when(leadRepository.findDuplicateCandidatesByName(
                        organizationId, "Ada", "Lovelace", "Analytical Engines", newLead.getId(), Lead.Status.CONVERTED))
                .thenReturn(List.of(existingLead));
        when(duplicateMatchRepository.findByOrganizationIdAndEntityTypeAndRecordAIdAndRecordBId(any(), any(), any(), any()))
                .thenReturn(Optional.empty());

        listener.onRecordCreated(new CrmAuditEvents.RecordCreated(actorUserId, organizationId, "Lead", newLead.getId()));

        ArgumentCaptor<DuplicateMatch> captor = ArgumentCaptor.forClass(DuplicateMatch.class);
        verify(duplicateMatchRepository).save(captor.capture());
        assertThat(captor.getValue().getMatchReason()).isEqualTo(DuplicateMatch.MatchReason.NAME);
    }

    @Test
    void onRecordCreated_leadNoEmailAndNoCompany_neverSearches() {
        Lead newLead = lead(null, null, Instant.now());
        when(leadRepository.findActiveByIdAndOrganizationId(newLead.getId(), organizationId)).thenReturn(Optional.of(newLead));

        listener.onRecordCreated(new CrmAuditEvents.RecordCreated(actorUserId, organizationId, "Lead", newLead.getId()));

        verify(leadRepository, never()).findDuplicateCandidatesByEmail(any(), any(), any(), any());
        verify(leadRepository, never()).findDuplicateCandidatesByName(any(), any(), any(), any(), any(), any());
    }

    @Test
    void onRecordCreated_convertedLead_isSkippedEntirely() {
        Lead newLead = lead("ada@example.com", null, Instant.now());
        newLead.setStatus(Lead.Status.CONVERTED);
        when(leadRepository.findActiveByIdAndOrganizationId(newLead.getId(), organizationId)).thenReturn(Optional.of(newLead));

        listener.onRecordCreated(new CrmAuditEvents.RecordCreated(actorUserId, organizationId, "Lead", newLead.getId()));

        verify(leadRepository, never()).findDuplicateCandidatesByEmail(any(), any(), any(), any());
    }

    @Test
    void onRecordCreated_contactNoEmail_fallsBackToNameOnly() {
        Contact newContact = new Contact(organizationId, "Grace", "Hopper", UUID.randomUUID());
        newContact.setId(UUID.randomUUID());
        newContact.setCreatedAt(Instant.now());
        Contact existingContact = new Contact(organizationId, "Grace", "Hopper", UUID.randomUUID());
        existingContact.setId(UUID.randomUUID());
        existingContact.setCreatedAt(Instant.now().minusSeconds(60));
        when(contactRepository.findActiveByIdAndOrganizationId(newContact.getId(), organizationId)).thenReturn(Optional.of(newContact));
        when(contactRepository.findDuplicateCandidatesByName(organizationId, "Grace", "Hopper", newContact.getId()))
                .thenReturn(List.of(existingContact));
        when(duplicateMatchRepository.findByOrganizationIdAndEntityTypeAndRecordAIdAndRecordBId(any(), any(), any(), any()))
                .thenReturn(Optional.empty());

        listener.onRecordCreated(new CrmAuditEvents.RecordCreated(actorUserId, organizationId, "Contact", newContact.getId()));

        ArgumentCaptor<DuplicateMatch> captor = ArgumentCaptor.forClass(DuplicateMatch.class);
        verify(duplicateMatchRepository).save(captor.capture());
        assertThat(captor.getValue().getEntityType()).isEqualTo(DuplicateMatch.EntityType.CONTACT);
        assertThat(captor.getValue().getMatchReason()).isEqualTo(DuplicateMatch.MatchReason.NAME);
    }

    @Test
    void onRecordCreated_accountMatchesByName_flagsThePair() {
        Account newAccount = new Account(organizationId, "Acme Rockets", UUID.randomUUID());
        newAccount.setId(UUID.randomUUID());
        newAccount.setCreatedAt(Instant.now());
        Account existingAccount = new Account(organizationId, "Acme Rockets", UUID.randomUUID());
        existingAccount.setId(UUID.randomUUID());
        existingAccount.setCreatedAt(Instant.now().minusSeconds(60));
        when(accountRepository.findActiveByIdAndOrganizationId(newAccount.getId(), organizationId)).thenReturn(Optional.of(newAccount));
        when(accountRepository.findDuplicateCandidatesByName(organizationId, "Acme Rockets", newAccount.getId()))
                .thenReturn(List.of(existingAccount));
        when(duplicateMatchRepository.findByOrganizationIdAndEntityTypeAndRecordAIdAndRecordBId(any(), any(), any(), any()))
                .thenReturn(Optional.empty());

        listener.onRecordCreated(new CrmAuditEvents.RecordCreated(actorUserId, organizationId, "Account", newAccount.getId()));

        verify(duplicateMatchRepository, times(1)).save(any());
    }

    private Lead lead(String email, String companyName, Instant createdAt) {
        Lead lead = new Lead(organizationId, "First", "Last", UUID.randomUUID());
        lead.setId(UUID.randomUUID());
        lead.setEmail(email);
        lead.setCompanyName(companyName);
        lead.setCreatedAt(createdAt);
        return lead;
    }
}
