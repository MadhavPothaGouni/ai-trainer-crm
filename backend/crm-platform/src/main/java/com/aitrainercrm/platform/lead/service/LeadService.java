package com.aitrainercrm.platform.lead.service;

import com.aitrainercrm.platform.account.entity.Account;
import com.aitrainercrm.platform.account.repository.AccountRepository;
import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.common.exception.BusinessException;
import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.contact.entity.Contact;
import com.aitrainercrm.platform.contact.repository.ContactRepository;
import com.aitrainercrm.platform.lead.dto.ConvertLeadRequest;
import com.aitrainercrm.platform.lead.dto.CreateLeadRequest;
import com.aitrainercrm.platform.lead.dto.LeadConversionResult;
import com.aitrainercrm.platform.lead.dto.UpdateLeadRequest;
import com.aitrainercrm.platform.lead.entity.Lead;
import com.aitrainercrm.platform.lead.repository.LeadRepository;
import com.aitrainercrm.platform.opportunity.entity.Opportunity;
import com.aitrainercrm.platform.opportunity.repository.OpportunityRepository;
import com.aitrainercrm.platform.role.entity.Permission;
import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.user.repository.UserRepository;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Unqualified prospects. {@link #convert} is the one method here that
 * reaches outside this module - it creates an Account (or links an
 * existing one), a Contact, and optionally an Opportunity, all in one
 * transaction, then marks the lead CONVERTED. Nothing else in this service
 * touches those three entities.
 */
@Service
@RequiredArgsConstructor
public class LeadService {

    private static final Permission.Resource RESOURCE = Permission.Resource.LEAD;

    private final LeadRepository leadRepository;
    private final AccountRepository accountRepository;
    private final ContactRepository contactRepository;
    private final OpportunityRepository opportunityRepository;
    private final UserRepository userRepository;
    private final ScopeAuthorizationService scopeAuthorizationService;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<Lead> list(UserPrincipal principal, Pageable pageable) {
        Optional<Set<UUID>> visibleOwnerIds = scopeAuthorizationService.visibleOwnerIds(principal, RESOURCE, Permission.Action.READ);
        return visibleOwnerIds
                .map(ownerIds -> leadRepository.findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(principal.getOrganizationId(), ownerIds, pageable))
                .orElseGet(() -> leadRepository.findByOrganizationIdAndDeletedAtIsNull(principal.getOrganizationId(), pageable));
    }

    @Transactional(readOnly = true)
    public Lead get(UserPrincipal principal, UUID leadId) {
        Lead lead = findOrThrow(principal.getOrganizationId(), leadId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.READ, lead.getOwnerId());
        return lead;
    }

    @Transactional
    public Lead create(UserPrincipal principal, CreateLeadRequest request) {
        UUID ownerId = resolveOwner(principal, Permission.Action.CREATE, request.ownerId());

        Lead lead = new Lead(principal.getOrganizationId(), request.firstName(), request.lastName(), ownerId);
        lead.setEmail(request.email());
        lead.setPhone(request.phone());
        lead.setCompanyName(request.companyName());
        lead.setTitle(request.title());
        lead.setSource(request.source());
        lead.setDescription(request.description());
        leadRepository.save(lead);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "Lead", lead.getId()));
        return lead;
    }

    @Transactional
    public Lead update(UserPrincipal principal, UUID leadId, UpdateLeadRequest request) {
        Lead lead = findOrThrow(principal.getOrganizationId(), leadId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, lead.getOwnerId());
        assertNotConverted(lead);

        lead.setFirstName(request.firstName());
        lead.setLastName(request.lastName());
        lead.setEmail(request.email());
        lead.setPhone(request.phone());
        lead.setCompanyName(request.companyName());
        lead.setTitle(request.title());
        lead.setSource(request.source());
        lead.setDescription(request.description());
        leadRepository.save(lead);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "Lead", lead.getId()));
        return lead;
    }

    @Transactional
    public Lead updateStatus(UserPrincipal principal, UUID leadId, Lead.Status newStatus) {
        if (newStatus == Lead.Status.CONVERTED) {
            throw new BusinessException("INVALID_STATUS_TRANSITION", "Use POST /api/v1/leads/{id}/convert to convert a lead", HttpStatus.BAD_REQUEST);
        }
        Lead lead = findOrThrow(principal.getOrganizationId(), leadId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, lead.getOwnerId());
        assertNotConverted(lead);

        lead.setStatus(newStatus);
        leadRepository.save(lead);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "Lead", lead.getId()));
        return lead;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID leadId) {
        Lead lead = findOrThrow(principal.getOrganizationId(), leadId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.DELETE, lead.getOwnerId());

        lead.setDeletedAt(Instant.now());
        leadRepository.save(lead);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "Lead", lead.getId()));
    }

    @Transactional
    public Lead assignOwner(UserPrincipal principal, UUID leadId, UUID newOwnerId) {
        Lead lead = findOrThrow(principal.getOrganizationId(), leadId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.ASSIGN, lead.getOwnerId());
        assertUserInOrganization(principal.getOrganizationId(), newOwnerId);

        lead.setOwnerId(newOwnerId);
        leadRepository.save(lead);

        events.publishEvent(new CrmAuditEvents.RecordAssigned(principal.getId(), principal.getOrganizationId(), "Lead", lead.getId(), newOwnerId));
        return lead;
    }

    /**
     * Creates (or links) an Account, creates a Contact, optionally creates an
     * Opportunity, and marks the lead CONVERTED - all four writes commit
     * together or not at all. Requires UPDATE on the lead (converting is a
     * mutation of it) plus CREATE on whichever of ACCOUNT/CONTACT/OPPORTUNITY
     * actually get created - a lead-owner without rights to create accounts
     * shouldn't be able to manufacture one just by converting a lead.
     */
    @Transactional
    public LeadConversionResult convert(UserPrincipal principal, UUID leadId, ConvertLeadRequest request) {
        Lead lead = findOrThrow(principal.getOrganizationId(), leadId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, lead.getOwnerId());
        assertNotConverted(lead);

        UUID accountId = resolveAccountForConversion(principal, lead, request);
        UUID contactId = createContactForConversion(principal, lead, accountId);
        UUID opportunityId = request.shouldCreateOpportunity() ? createOpportunityForConversion(principal, lead, accountId, contactId, request) : null;

        lead.setStatus(Lead.Status.CONVERTED);
        lead.setConvertedAccountId(accountId);
        lead.setConvertedContactId(contactId);
        lead.setConvertedOpportunityId(opportunityId);
        lead.setConvertedAt(Instant.now());
        leadRepository.save(lead);

        events.publishEvent(new CrmAuditEvents.LeadConverted(principal.getId(), principal.getOrganizationId(), lead.getId(), accountId, contactId, opportunityId));
        return LeadConversionResult.builder().leadId(lead.getId()).accountId(accountId).contactId(contactId).opportunityId(opportunityId).build();
    }

    private UUID resolveAccountForConversion(UserPrincipal principal, Lead lead, ConvertLeadRequest request) {
        if (request.existingAccountId() != null) {
            if (!accountRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(request.existingAccountId(), principal.getOrganizationId())) {
                throw new ResourceNotFoundException("Account", request.existingAccountId());
            }
            return request.existingAccountId();
        }

        assertCanCreate(principal, Permission.Resource.ACCOUNT);
        String name = firstNonBlank(request.newAccountName(), lead.getCompanyName(), lead.getFullName() + "'s Account");
        Account account = new Account(principal.getOrganizationId(), name, lead.getOwnerId());
        accountRepository.save(account);
        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "Account", account.getId()));
        return account.getId();
    }

    private UUID createContactForConversion(UserPrincipal principal, Lead lead, UUID accountId) {
        assertCanCreate(principal, Permission.Resource.CONTACT);
        Contact contact = new Contact(principal.getOrganizationId(), lead.getFirstName(), lead.getLastName(), lead.getOwnerId());
        contact.setEmail(lead.getEmail());
        contact.setPhone(lead.getPhone());
        contact.setTitle(lead.getTitle());
        contact.setAccountId(accountId);
        contactRepository.save(contact);
        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "Contact", contact.getId()));
        return contact.getId();
    }

    private UUID createOpportunityForConversion(UserPrincipal principal, Lead lead, UUID accountId, UUID contactId, ConvertLeadRequest request) {
        assertCanCreate(principal, Permission.Resource.OPPORTUNITY);
        String name = firstNonBlank(request.opportunityName(), lead.getFullName() + " Opportunity");
        Opportunity opportunity = new Opportunity(principal.getOrganizationId(), accountId, name, lead.getOwnerId());
        opportunity.setPrimaryContactId(contactId);
        opportunity.setAmount(request.opportunityAmount());
        opportunity.setExpectedCloseDate(request.opportunityExpectedCloseDate());
        opportunityRepository.save(opportunity);
        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "Opportunity", opportunity.getId()));
        return opportunity.getId();
    }

    private void assertCanCreate(UserPrincipal principal, Permission.Resource resource) {
        if (scopeAuthorizationService.highestGranted(principal, resource, Permission.Action.CREATE) == ScopeAuthorizationService.Access.NONE) {
            throw new ForbiddenException("You don't have permission to create " + resource.name().toLowerCase(Locale.ROOT) + "s");
        }
    }

    private void assertNotConverted(Lead lead) {
        if (lead.isConverted()) {
            throw new BusinessException("LEAD_ALREADY_CONVERTED", "This lead has already been converted", HttpStatus.CONFLICT);
        }
    }

    private String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        throw new IllegalStateException("No non-blank candidate available");
    }

    private Lead findOrThrow(UUID organizationId, UUID leadId) {
        return leadRepository.findActiveByIdAndOrganizationId(leadId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Lead", leadId));
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
}
