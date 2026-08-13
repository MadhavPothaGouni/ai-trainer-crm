package com.aitrainercrm.platform.gdpr.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aitrainercrm.platform.contact.entity.Contact;
import com.aitrainercrm.platform.contact.repository.ContactRepository;
import com.aitrainercrm.platform.gdpr.dto.DataSubjectExportDto;
import com.aitrainercrm.platform.gdpr.entity.DataSubjectRequest;
import com.aitrainercrm.platform.gdpr.repository.DataSubjectRequestRepository;
import com.aitrainercrm.platform.lead.entity.Lead;
import com.aitrainercrm.platform.lead.repository.LeadRepository;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class DataSubjectRequestServiceTest {

    @Mock private DataSubjectRequestRepository dataSubjectRequestRepository;
    @Mock private ContactRepository contactRepository;
    @Mock private LeadRepository leadRepository;
    @Mock private ApplicationEventPublisher events;

    private DataSubjectRequestService service;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final String email = "jane@example.com";

    private UserPrincipal principal;

    @BeforeEach
    void setUp() {
        service = new DataSubjectRequestService(dataSubjectRequestRepository, contactRepository, leadRepository, events);
        principal = new UserPrincipal(ownerId, "admin@example.com", organizationId, List.of("DATA_SUBJECT_REQUEST:EXPORT:ORGANIZATION"));
    }

    @Test
    void export_gathersMatchingContactsAndLeads_andPersistsARequestRow() {
        Contact contact = new Contact(organizationId, "Jane", "Doe", ownerId);
        contact.setEmail(email);
        Lead lead = new Lead(organizationId, "Jane", "Doe", ownerId);
        lead.setEmail(email);
        when(contactRepository.findByOrganizationIdAndEmailIgnoreCase(organizationId, email)).thenReturn(List.of(contact));
        when(leadRepository.findByOrganizationIdAndEmailIgnoreCase(organizationId, email)).thenReturn(List.of(lead));

        DataSubjectExportDto result = service.export(principal, email);

        assertThat(result.contacts()).hasSize(1);
        assertThat(result.contacts().get(0).firstName()).isEqualTo("Jane");
        assertThat(result.leads()).hasSize(1);

        ArgumentCaptor<DataSubjectRequest> captor = ArgumentCaptor.forClass(DataSubjectRequest.class);
        verify(dataSubjectRequestRepository).save(captor.capture());
        assertThat(captor.getValue().getRequestType()).isEqualTo(DataSubjectRequest.RequestType.EXPORT);
        assertThat(captor.getValue().getContactsAffected()).isEqualTo(1);
        assertThat(captor.getValue().getLeadsAffected()).isEqualTo(1);
        assertThat(captor.getValue().getStatus()).isEqualTo(DataSubjectRequest.Status.COMPLETED);
    }

    @Test
    void export_noMatches_stillPersistsACompletedRequestWithZeroCounts() {
        when(contactRepository.findByOrganizationIdAndEmailIgnoreCase(organizationId, email)).thenReturn(List.of());
        when(leadRepository.findByOrganizationIdAndEmailIgnoreCase(organizationId, email)).thenReturn(List.of());

        DataSubjectExportDto result = service.export(principal, email);

        assertThat(result.contacts()).isEmpty();
        assertThat(result.leads()).isEmpty();
        ArgumentCaptor<DataSubjectRequest> captor = ArgumentCaptor.forClass(DataSubjectRequest.class);
        verify(dataSubjectRequestRepository).save(captor.capture());
        assertThat(captor.getValue().getContactsAffected()).isZero();
        assertThat(captor.getValue().getStatus()).isEqualTo(DataSubjectRequest.Status.COMPLETED);
    }

    @Test
    void erase_scrubsPiiColumns_andSoftDeletes() {
        Contact contact = new Contact(organizationId, "Jane", "Doe", ownerId);
        contact.setEmail(email);
        contact.setPhone("555-1234");
        Lead lead = new Lead(organizationId, "Jane", "Doe", ownerId);
        lead.setEmail(email);
        lead.setCompanyName("Acme");
        when(contactRepository.findByOrganizationIdAndEmailIgnoreCase(organizationId, email)).thenReturn(List.of(contact));
        when(leadRepository.findByOrganizationIdAndEmailIgnoreCase(organizationId, email)).thenReturn(List.of(lead));

        DataSubjectRequest result = service.erase(principal, email);

        assertThat(contact.getFirstName()).isEqualTo("Redacted");
        assertThat(contact.getLastName()).isEqualTo("Redacted");
        assertThat(contact.getEmail()).isNull();
        assertThat(contact.getPhone()).isNull();
        assertThat(contact.getDeletedAt()).isNotNull();

        assertThat(lead.getFirstName()).isEqualTo("Redacted");
        assertThat(lead.getEmail()).isNull();
        assertThat(lead.getCompanyName()).isNull();
        assertThat(lead.getDeletedAt()).isNotNull();

        assertThat(result.getRequestType()).isEqualTo(DataSubjectRequest.RequestType.ERASURE);
        assertThat(result.getContactsAffected()).isEqualTo(1);
        assertThat(result.getLeadsAffected()).isEqualTo(1);

        verify(contactRepository).saveAll(List.of(contact));
        verify(leadRepository).saveAll(List.of(lead));
        verify(events).publishEvent(any());
    }

    @Test
    void erase_alreadySoftDeletedContact_doesNotOverwriteTheOriginalDeletedAt() {
        Contact contact = new Contact(organizationId, "Jane", "Doe", ownerId);
        contact.setEmail(email);
        Instant originalDeletedAt = Instant.now().minusSeconds(3600);
        contact.setDeletedAt(originalDeletedAt);
        when(contactRepository.findByOrganizationIdAndEmailIgnoreCase(organizationId, email)).thenReturn(List.of(contact));
        when(leadRepository.findByOrganizationIdAndEmailIgnoreCase(organizationId, email)).thenReturn(List.of());

        service.erase(principal, email);

        assertThat(contact.getDeletedAt()).isEqualTo(originalDeletedAt);
        assertThat(contact.getFirstName()).isEqualTo("Redacted");
    }

    @Test
    void erase_noMatches_isANoOpThatStillPersistsARequestRow() {
        when(contactRepository.findByOrganizationIdAndEmailIgnoreCase(organizationId, email)).thenReturn(List.of());
        when(leadRepository.findByOrganizationIdAndEmailIgnoreCase(organizationId, email)).thenReturn(List.of());

        DataSubjectRequest result = service.erase(principal, email);

        assertThat(result.getContactsAffected()).isZero();
        assertThat(result.getLeadsAffected()).isZero();
        verify(dataSubjectRequestRepository).save(result);
    }
}
