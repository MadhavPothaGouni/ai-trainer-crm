package com.aitrainercrm.platform.dedupe.listener;

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
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Flags likely-duplicate Lead/Contact/Account pairs - the fifth independent {@code
 * @EventListener} on the {@link CrmAuditEvents} bus, alongside {@code WebhookDispatchListener},
 * {@code AuditEventListener}, {@code WorkflowEngineListener}, and {@code
 * TerritoryAssignmentListener}. Purely additive, like every listener on this bus except {@code
 * TerritoryAssignmentListener}: this one only ever creates a new {@link DuplicateMatch} row,
 * never touches the Lead/Contact/Account it's evaluating.
 *
 * <p>Only {@code onRecordCreated} exists here, deliberately, the same "fires once, at creation"
 * scope limit {@code TerritoryAssignmentListener} documents - a record's fields are checked for
 * duplicates against the rest of the organization exactly once, at the moment it's created. If
 * someone edits a Lead's email a week later to match an existing Contact, that edit will never
 * retroactively get flagged; a real product would likely also re-check on update, but that's more
 * scope than this pass takes on.
 *
 * <p><b>Matching rules</b>, in priority order: email first (an exact, case-insensitive match -
 * strongest signal), then a name-based fallback only when the record has no email at all. Lead's
 * fallback is first+last+company (all three, since two different people can plausibly share a
 * first+last name); Contact's fallback is first+last only, since {@link Contact} has no company
 * field to scope by - see {@code ContactRepository#findDuplicateCandidatesByName}'s javadoc for
 * why a NAME-reason Contact match is a weaker signal than an EMAIL-reason one. Account has no
 * email at all, so it only ever matches by name. A Lead that's already {@code CONVERTED} is
 * excluded from ever being a match candidate - its identity has effectively moved to whatever
 * Account/Contact/Opportunity it converted into.
 */
@Component
@RequiredArgsConstructor
public class DuplicateDetectionListener {

    private final LeadRepository leadRepository;
    private final ContactRepository contactRepository;
    private final AccountRepository accountRepository;
    private final DuplicateMatchRepository duplicateMatchRepository;

    // @TransactionalEventListener(AFTER_COMMIT), not plain @EventListener: detectForLead/Contact/
    // Account below re-read the just-created record (and run duplicate-candidate queries against
    // the rest of the table) from the database, and the owning service publishes RecordCreated
    // from inside its own @Transactional create method, before that transaction commits. A plain
    // @Async @EventListener risks starting before the publisher's insert has committed, on a
    // separate connection that can't see the still-uncommitted row yet. fallbackExecution=true
    // preserves the previous behavior if ever published outside a transaction.
    // propagation=REQUIRES_NEW is required, not optional: Spring's
    // RestrictedTransactionalEventListenerFactory rejects a plain @Transactional on an AFTER_COMMIT
    // listener at context-startup time, since by AFTER_COMMIT the publisher's transaction has
    // already committed and closed, so REQUIRED propagation would be implicitly opening a
    // brand-new transaction anyway - Spring requires that intent to be explicit.
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRecordCreated(CrmAuditEvents.RecordCreated event) {
        switch (event.resourceType()) {
            case "Lead" -> detectForLead(event);
            case "Contact" -> detectForContact(event);
            case "Account" -> detectForAccount(event);
            default -> { /* not a duplicate-detectable resource - the normal case for every other event on this bus */ }
        }
    }

    private void detectForLead(CrmAuditEvents.RecordCreated event) {
        Lead lead = leadRepository.findActiveByIdAndOrganizationId(event.resourceId(), event.organizationId()).orElse(null);
        if (lead == null || lead.getStatus() == Lead.Status.CONVERTED) return;

        List<Lead> candidates;
        DuplicateMatch.MatchReason reason;
        if (lead.getEmail() != null && !lead.getEmail().isBlank()) {
            candidates = leadRepository.findDuplicateCandidatesByEmail(
                    event.organizationId(), lead.getEmail(), lead.getId(), Lead.Status.CONVERTED);
            reason = DuplicateMatch.MatchReason.EMAIL;
        } else if (lead.getCompanyName() != null && !lead.getCompanyName().isBlank()) {
            candidates = leadRepository.findDuplicateCandidatesByName(
                    event.organizationId(), lead.getFirstName(), lead.getLastName(), lead.getCompanyName(), lead.getId(),
                    Lead.Status.CONVERTED);
            reason = DuplicateMatch.MatchReason.NAME;
        } else {
            return;
        }

        for (Lead candidate : candidates) {
            flag(event.organizationId(), DuplicateMatch.EntityType.LEAD, lead.getId(), lead.getCreatedAt(),
                    candidate.getId(), candidate.getCreatedAt(), reason);
        }
    }

    private void detectForContact(CrmAuditEvents.RecordCreated event) {
        Contact contact = contactRepository.findActiveByIdAndOrganizationId(event.resourceId(), event.organizationId()).orElse(null);
        if (contact == null) return;

        List<Contact> candidates;
        DuplicateMatch.MatchReason reason;
        if (contact.getEmail() != null && !contact.getEmail().isBlank()) {
            candidates = contactRepository.findDuplicateCandidatesByEmail(event.organizationId(), contact.getEmail(), contact.getId());
            reason = DuplicateMatch.MatchReason.EMAIL;
        } else {
            candidates = contactRepository.findDuplicateCandidatesByName(
                    event.organizationId(), contact.getFirstName(), contact.getLastName(), contact.getId());
            reason = DuplicateMatch.MatchReason.NAME;
        }

        for (Contact candidate : candidates) {
            flag(event.organizationId(), DuplicateMatch.EntityType.CONTACT, contact.getId(), contact.getCreatedAt(),
                    candidate.getId(), candidate.getCreatedAt(), reason);
        }
    }

    private void detectForAccount(CrmAuditEvents.RecordCreated event) {
        Account account = accountRepository.findActiveByIdAndOrganizationId(event.resourceId(), event.organizationId()).orElse(null);
        if (account == null) return;

        List<Account> candidates =
                accountRepository.findDuplicateCandidatesByName(event.organizationId(), account.getName(), account.getId());
        for (Account candidate : candidates) {
            flag(event.organizationId(), DuplicateMatch.EntityType.ACCOUNT, account.getId(), account.getCreatedAt(),
                    candidate.getId(), candidate.getCreatedAt(), DuplicateMatch.MatchReason.NAME);
        }
    }

    /** Normalizes (newId, candidateId) into (recordA, recordB) by created-at, tie-broken by id - see V23's migration comment - then skips creating a row if that exact pair is already flagged, so re-detection can never double-flag the same two records. */
    private void flag(
            UUID organizationId, DuplicateMatch.EntityType entityType, UUID newId, Instant newCreatedAt, UUID candidateId,
            Instant candidateCreatedAt, DuplicateMatch.MatchReason reason) {
        UUID recordAId;
        UUID recordBId;
        if (candidateCreatedAt.isBefore(newCreatedAt) || (candidateCreatedAt.equals(newCreatedAt) && candidateId.compareTo(newId) < 0)) {
            recordAId = candidateId;
            recordBId = newId;
        } else {
            recordAId = newId;
            recordBId = candidateId;
        }

        boolean alreadyFlagged = duplicateMatchRepository
                .findByOrganizationIdAndEntityTypeAndRecordAIdAndRecordBId(organizationId, entityType, recordAId, recordBId)
                .isPresent();
        if (alreadyFlagged) return;

        duplicateMatchRepository.save(new DuplicateMatch(organizationId, entityType, recordAId, recordBId, reason));
    }
}
