package com.aitrainercrm.platform.gdpr.service;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
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
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Backs {@code DATA_SUBJECT_REQUEST:EXPORT/DELETE/READ:ORGANIZATION}. See V30's migration comment
 * for the full design reasoning - subjects are identified by email (not a specific Contact/Lead
 * id), {@link #erase} scrubs PII columns in place rather than hard-deleting rows so FK-referencing
 * Activities/Opportunities keep working exactly as they do for an ordinary soft delete, and both
 * lookups deliberately reach already-soft-deleted rows too.
 *
 * <p>Like {@code ImportExportService#runImport}, both {@link #export} and {@link #erase} persist a
 * {@link DataSubjectRequest} row unconditionally, including when zero Contacts/Leads match the
 * email - a "nobody found" result is still a legitimate, auditable answer to "what do you have on
 * this person," not a failure.
 */
@Service
@RequiredArgsConstructor
public class DataSubjectRequestService {

    private static final String REDACTED = "Redacted";

    private final DataSubjectRequestRepository dataSubjectRequestRepository;
    private final ContactRepository contactRepository;
    private final LeadRepository leadRepository;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<DataSubjectRequest> list(UUID organizationId, Pageable pageable) {
        return dataSubjectRequestRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId, pageable);
    }

    @Transactional(readOnly = true)
    public DataSubjectRequest get(UUID organizationId, UUID requestId) {
        return dataSubjectRequestRepository.findByIdAndOrganizationId(requestId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("DataSubjectRequest", requestId));
    }

    @Transactional
    public DataSubjectExportDto export(UserPrincipal principal, String subjectEmail) {
        List<Contact> contacts = contactRepository.findByOrganizationIdAndEmailIgnoreCase(principal.getOrganizationId(), subjectEmail);
        List<Lead> leads = leadRepository.findByOrganizationIdAndEmailIgnoreCase(principal.getOrganizationId(), subjectEmail);

        DataSubjectRequest request =
                new DataSubjectRequest(principal.getOrganizationId(), DataSubjectRequest.RequestType.EXPORT, subjectEmail, principal.getId());
        request.setContactsAffected(contacts.size());
        request.setLeadsAffected(leads.size());
        request.setCompletedAt(Instant.now());
        dataSubjectRequestRepository.save(request);

        return DataSubjectExportDto.builder()
                .subjectEmail(subjectEmail)
                .exportedAt(request.getCompletedAt())
                .contacts(contacts.stream().map(DataSubjectExportDto.ExportedContact::from).toList())
                .leads(leads.stream().map(DataSubjectExportDto.ExportedLead::from).toList())
                .build();
    }

    @Transactional
    public DataSubjectRequest erase(UserPrincipal principal, String subjectEmail) {
        List<Contact> contacts = contactRepository.findByOrganizationIdAndEmailIgnoreCase(principal.getOrganizationId(), subjectEmail);
        List<Lead> leads = leadRepository.findByOrganizationIdAndEmailIgnoreCase(principal.getOrganizationId(), subjectEmail);

        contacts.forEach(this::redactContact);
        leads.forEach(this::redactLead);
        contactRepository.saveAll(contacts);
        leadRepository.saveAll(leads);

        contacts.forEach(c -> events.publishEvent(
                new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "Contact", c.getId())));
        leads.forEach(l -> events.publishEvent(
                new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "Lead", l.getId())));

        DataSubjectRequest request = new DataSubjectRequest(
                principal.getOrganizationId(), DataSubjectRequest.RequestType.ERASURE, subjectEmail, principal.getId());
        request.setContactsAffected(contacts.size());
        request.setLeadsAffected(leads.size());
        request.setCompletedAt(Instant.now());
        dataSubjectRequestRepository.save(request);
        return request;
    }

    /**
     * Overwrites every PII column with a fixed placeholder and soft-deletes the row if it isn't
     * already - deliberately leaves {@code accountId}/{@code ownerId} and the row itself alone so
     * every Activity/Opportunity FK-referencing this Contact keeps resolving, the same reasoning
     * {@link Contact#deletedAt}'s own javadoc gives for why an ordinary soft delete never cascades.
     * Email is nulled rather than redacted to a placeholder string - unlike name, a fabricated email
     * could collide with a real person's address in {@code findDuplicateCandidatesByEmail} and other
     * exact-match lookups elsewhere in the codebase.
     */
    private void redactContact(Contact contact) {
        contact.setFirstName(REDACTED);
        contact.setLastName(REDACTED);
        contact.setEmail(null);
        contact.setPhone(null);
        contact.setTitle(null);
        contact.setDescription(null);
        if (contact.getDeletedAt() == null) {
            contact.setDeletedAt(Instant.now());
        }
    }

    private void redactLead(Lead lead) {
        lead.setFirstName(REDACTED);
        lead.setLastName(REDACTED);
        lead.setEmail(null);
        lead.setPhone(null);
        lead.setCompanyName(null);
        lead.setTitle(null);
        lead.setDescription(null);
        if (lead.getDeletedAt() == null) {
            lead.setDeletedAt(Instant.now());
        }
    }
}
