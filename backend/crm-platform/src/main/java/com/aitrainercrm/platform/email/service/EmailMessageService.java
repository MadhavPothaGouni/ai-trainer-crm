package com.aitrainercrm.platform.email.service;

import com.aitrainercrm.platform.account.repository.AccountRepository;
import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.common.util.CsvWriter;
import com.aitrainercrm.platform.contact.repository.ContactRepository;
import com.aitrainercrm.platform.email.dto.LogEmailRequest;
import com.aitrainercrm.platform.email.entity.EmailMessage;
import com.aitrainercrm.platform.email.repository.EmailMessageRepository;
import com.aitrainercrm.platform.lead.repository.LeadRepository;
import com.aitrainercrm.platform.opportunity.repository.OpportunityRepository;
import com.aitrainercrm.platform.role.entity.Permission;
import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.ticket.repository.TicketRepository;
import com.aitrainercrm.platform.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Logged emails against an Account/Contact/Opportunity/Lead/Ticket. Same
 * shape as {@code TicketService}: OWN/TEAM/DEPARTMENT/ORGANIZATION record-
 * level authorization via {@link ScopeAuthorizationService}, {@code
 * resolveOwner} defaulting a null {@code ownerId} to the caller - plus one
 * extra step ({@code validateRelatedTo}) borrowed from {@code
 * ActivityService}, since an email's "what this is about" reference isn't a
 * single fixed entity type.
 */
@Service
@RequiredArgsConstructor
public class EmailMessageService {

    private static final Permission.Resource RESOURCE = Permission.Resource.EMAIL_MESSAGE;

    private final EmailMessageRepository emailMessageRepository;
    private final AccountRepository accountRepository;
    private final ContactRepository contactRepository;
    private final OpportunityRepository opportunityRepository;
    private final LeadRepository leadRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final ScopeAuthorizationService scopeAuthorizationService;
    private final ApplicationEventPublisher events;

    /** relatedToType/relatedToId are optional - when both are given, the list is additionally filtered to that one record's email history. */
    @Transactional(readOnly = true)
    public Page<EmailMessage> list(
            UserPrincipal principal, EmailMessage.RelatedToType relatedToType, UUID relatedToId, Pageable pageable) {
        Optional<Set<UUID>> visibleOwnerIds = scopeAuthorizationService.visibleOwnerIds(principal, RESOURCE, Permission.Action.READ);
        UUID organizationId = principal.getOrganizationId();

        if (relatedToType != null && relatedToId != null) {
            return visibleOwnerIds
                    .map(ownerIds -> emailMessageRepository.findByOrganizationIdAndOwnerIdInAndRelatedToTypeAndRelatedToIdAndDeletedAtIsNullOrderBySentAtDesc(
                            organizationId, ownerIds, relatedToType, relatedToId, pageable))
                    .orElseGet(() -> emailMessageRepository.findByOrganizationIdAndRelatedToTypeAndRelatedToIdAndDeletedAtIsNullOrderBySentAtDesc(
                            organizationId, relatedToType, relatedToId, pageable));
        }

        return visibleOwnerIds
                .map(ownerIds -> emailMessageRepository.findByOrganizationIdAndOwnerIdInAndDeletedAtIsNullOrderBySentAtDesc(organizationId, ownerIds, pageable))
                .orElseGet(() -> emailMessageRepository.findByOrganizationIdAndDeletedAtIsNullOrderBySentAtDesc(organizationId, pageable));
    }

    @Transactional(readOnly = true)
    public EmailMessage get(UserPrincipal principal, UUID emailId) {
        EmailMessage email = findOrThrow(principal.getOrganizationId(), emailId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.READ, email.getOwnerId());
        return email;
    }

    /** Backs GET /email-messages/export (EMAIL_MESSAGE:EXPORT) - same shape as CampaignService#exportCsv/AccountService's export. */
    @Transactional(readOnly = true)
    public byte[] exportCsv(UserPrincipal principal) {
        Optional<Set<UUID>> visibleOwnerIds = scopeAuthorizationService.visibleOwnerIds(principal, RESOURCE, Permission.Action.EXPORT);
        UUID organizationId = principal.getOrganizationId();
        List<EmailMessage> emails = visibleOwnerIds
                .map(ownerIds -> emailMessageRepository.findByOrganizationIdAndOwnerIdInAndDeletedAtIsNullOrderBySentAtDesc(organizationId, ownerIds))
                .orElseGet(() -> emailMessageRepository.findByOrganizationIdAndDeletedAtIsNullOrderBySentAtDesc(organizationId));

        CsvWriter csv = new CsvWriter().row(
                "Direction", "Subject", "From", "To", "Cc", "Related To Type", "Related To Id", "Sent At", "Created At");
        for (EmailMessage email : emails) {
            csv.row(
                    email.getDirection(), email.getSubject(), email.getFromAddress(), email.getToAddresses(), email.getCcAddresses(),
                    email.getRelatedToType(), email.getRelatedToId(), email.getSentAt(), email.getCreatedAt());
        }
        return csv.toBytes();
    }

    @Transactional
    public EmailMessage create(UserPrincipal principal, LogEmailRequest request) {
        UUID ownerId = resolveOwner(principal, Permission.Action.CREATE, request.ownerId());
        validateRelatedTo(principal.getOrganizationId(), request.relatedToType(), request.relatedToId());

        EmailMessage email = new EmailMessage(
                principal.getOrganizationId(), request.direction(), request.subject(), request.fromAddress(), request.toAddresses(),
                request.relatedToType(), request.relatedToId(), request.sentAt() != null ? request.sentAt() : Instant.now(), ownerId);
        email.setBody(request.body());
        email.setCcAddresses(request.ccAddresses());
        emailMessageRepository.save(email);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "EmailMessage", email.getId()));
        return email;
    }

    @Transactional
    public EmailMessage update(UserPrincipal principal, UUID emailId, LogEmailRequest request) {
        EmailMessage email = findOrThrow(principal.getOrganizationId(), emailId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, email.getOwnerId());
        validateRelatedTo(principal.getOrganizationId(), request.relatedToType(), request.relatedToId());

        email.setDirection(request.direction());
        email.setSubject(request.subject());
        email.setBody(request.body());
        email.setFromAddress(request.fromAddress());
        email.setToAddresses(request.toAddresses());
        email.setCcAddresses(request.ccAddresses());
        email.setRelatedToType(request.relatedToType());
        email.setRelatedToId(request.relatedToId());
        email.setSentAt(request.sentAt() != null ? request.sentAt() : email.getSentAt());
        emailMessageRepository.save(email);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "EmailMessage", email.getId()));
        return email;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID emailId) {
        EmailMessage email = findOrThrow(principal.getOrganizationId(), emailId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.DELETE, email.getOwnerId());

        email.setDeletedAt(Instant.now());
        emailMessageRepository.save(email);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "EmailMessage", emailId));
    }

    @Transactional
    public EmailMessage assignOwner(UserPrincipal principal, UUID emailId, UUID newOwnerId) {
        EmailMessage email = findOrThrow(principal.getOrganizationId(), emailId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.ASSIGN, email.getOwnerId());
        assertUserInOrganization(principal.getOrganizationId(), newOwnerId);

        email.setOwnerId(newOwnerId);
        emailMessageRepository.save(email);

        events.publishEvent(new CrmAuditEvents.RecordAssigned(principal.getId(), principal.getOrganizationId(), "EmailMessage", email.getId(), newOwnerId));
        return email;
    }

    private EmailMessage findOrThrow(UUID organizationId, UUID emailId) {
        return emailMessageRepository.findActiveByIdAndOrganizationId(emailId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("EmailMessage", emailId));
    }

    private UUID resolveOwner(UserPrincipal principal, Permission.Action action, UUID requestedOwnerId) {
        if (requestedOwnerId == null || requestedOwnerId.equals(principal.getId())) {
            return principal.getId();
        }
        if (scopeAuthorizationService.highestGranted(principal, RESOURCE, action) != ScopeAuthorizationService.Access.ORGANIZATION) {
            throw new ForbiddenException("You can only " + action.name().toLowerCase(Locale.ROOT) + " records assigned to yourself");
        }
        assertUserInOrganization(principal.getOrganizationId(), requestedOwnerId);
        return requestedOwnerId;
    }

    private void assertUserInOrganization(UUID organizationId, UUID userId) {
        boolean exists = userRepository.findActiveById(userId).map(u -> organizationId.equals(u.getOrganizationId())).orElse(false);
        if (!exists) {
            throw new ResourceNotFoundException("User", userId);
        }
    }

    /** No FK to lean on (see V15's migration comment) - checks the reference exists, in this tenant, against whichever of the five repositories relatedToType names. */
    private void validateRelatedTo(UUID organizationId, EmailMessage.RelatedToType relatedToType, UUID relatedToId) {
        boolean exists = switch (relatedToType) {
            case ACCOUNT -> accountRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(relatedToId, organizationId);
            case CONTACT -> contactRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(relatedToId, organizationId);
            case OPPORTUNITY -> opportunityRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(relatedToId, organizationId);
            case LEAD -> leadRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(relatedToId, organizationId);
            case TICKET -> ticketRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(relatedToId, organizationId);
        };
        if (!exists) {
            throw new ResourceNotFoundException(relatedToType.name(), relatedToId);
        }
    }
}
