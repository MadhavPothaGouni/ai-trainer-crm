package com.aitrainercrm.platform.campaign.service;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.campaign.dto.AddCampaignMemberRequest;
import com.aitrainercrm.platform.campaign.dto.CampaignStatsDto;
import com.aitrainercrm.platform.campaign.dto.CreateCampaignRequest;
import com.aitrainercrm.platform.campaign.dto.UpdateCampaignRequest;
import com.aitrainercrm.platform.campaign.entity.Campaign;
import com.aitrainercrm.platform.campaign.entity.CampaignMember;
import com.aitrainercrm.platform.campaign.repository.CampaignMemberRepository;
import com.aitrainercrm.platform.campaign.repository.CampaignRepository;
import com.aitrainercrm.platform.common.exception.BusinessException;
import com.aitrainercrm.platform.common.exception.DuplicateResourceException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.common.util.CsvWriter;
import com.aitrainercrm.platform.contact.repository.ContactRepository;
import com.aitrainercrm.platform.lead.repository.LeadRepository;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Campaigns and their members. Same shared-org-resource pattern as
 * {@code OrderService}/{@code ProductService} - no
 * {@code ScopeAuthorizationService} calls; the controller's static
 * {@code @PreAuthorize} is the whole authorization story. Member mutations
 * are gated on {@code CAMPAIGN:UPDATE} against the parent campaign, same
 * reasoning as Quote/Order's line items - there's no separate
 * campaign-member permission in the catalog.
 */
@Service
@RequiredArgsConstructor
public class CampaignService {

    private final CampaignRepository campaignRepository;
    private final CampaignMemberRepository memberRepository;
    private final LeadRepository leadRepository;
    private final ContactRepository contactRepository;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<Campaign> list(UserPrincipal principal, Pageable pageable) {
        return campaignRepository.findByOrganizationIdAndDeletedAtIsNull(principal.getOrganizationId(), pageable);
    }

    @Transactional(readOnly = true)
    public Campaign get(UserPrincipal principal, UUID campaignId) {
        return findOrThrow(principal.getOrganizationId(), campaignId);
    }

    /**
     * Backs {@code GET /campaigns/export} (CAMPAIGN:EXPORT) - the first real
     * implementation of the EXPORT permission anywhere in this codebase; it
     * was seeded into the catalog back in V2 alongside every core CRM
     * resource but nothing had actually built the feature it names until
     * now. One row per campaign, unpaginated (an export needs every row in
     * one pass, not a page at a time).
     */
    @Transactional(readOnly = true)
    public byte[] exportCsv(UserPrincipal principal) {
        List<Campaign> campaigns = campaignRepository.findByOrganizationIdAndDeletedAtIsNullOrderByCreatedAtDesc(principal.getOrganizationId());
        CsvWriter csv = new CsvWriter().row("Name", "Type", "Status", "Start Date", "End Date", "Budget", "Actual Cost", "Created At");
        for (Campaign campaign : campaigns) {
            csv.row(
                    campaign.getName(), campaign.getType(), campaign.getStatus(), campaign.getStartDate(), campaign.getEndDate(),
                    campaign.getBudget(), campaign.getActualCost(), campaign.getCreatedAt());
        }
        return csv.toBytes();
    }

    @Transactional
    public Campaign create(UserPrincipal principal, CreateCampaignRequest request) {
        Campaign campaign = new Campaign(principal.getOrganizationId(), request.name(), request.type());
        applyFields(campaign, request.startDate(), request.endDate(), request.budget(), request.actualCost(), request.description());
        campaignRepository.save(campaign);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "Campaign", campaign.getId()));
        return campaign;
    }

    @Transactional
    public Campaign update(UserPrincipal principal, UUID campaignId, UpdateCampaignRequest request) {
        Campaign campaign = findOrThrow(principal.getOrganizationId(), campaignId);
        campaign.setName(request.name());
        campaign.setType(request.type());
        applyFields(campaign, request.startDate(), request.endDate(), request.budget(), request.actualCost(), request.description());
        campaignRepository.save(campaign);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "Campaign", campaign.getId()));
        return campaign;
    }

    /** PLANNED -&gt; ACTIVE -&gt; COMPLETED, CANCELLED reachable from PLANNED or ACTIVE. No APPROVE action seeded for CAMPAIGN, so this is gated on plain CAMPAIGN:UPDATE - see Campaign's javadoc. */
    @Transactional
    public Campaign updateStatus(UserPrincipal principal, UUID campaignId, Campaign.Status status) {
        Campaign campaign = findOrThrow(principal.getOrganizationId(), campaignId);
        validateStatusTransition(campaign.getStatus(), status);

        campaign.setStatus(status);
        campaignRepository.save(campaign);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "Campaign", campaign.getId()));
        return campaign;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID campaignId) {
        Campaign campaign = findOrThrow(principal.getOrganizationId(), campaignId);
        campaign.setDeletedAt(Instant.now());
        campaignRepository.save(campaign);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "Campaign", campaignId));
    }

    @Transactional(readOnly = true)
    public List<CampaignMember> getMembers(UserPrincipal principal, UUID campaignId) {
        findOrThrow(principal.getOrganizationId(), campaignId); // validates existence
        return memberRepository.findByCampaignIdOrderByCreatedAtAsc(campaignId);
    }

    @Transactional(readOnly = true)
    public CampaignStatsDto getStats(UserPrincipal principal, UUID campaignId) {
        findOrThrow(principal.getOrganizationId(), campaignId); // validates existence
        return CampaignStatsDto.from(campaignId, memberRepository.countByStatus(campaignId));
    }

    @Transactional
    public CampaignMember addMember(UserPrincipal principal, UUID campaignId, AddCampaignMemberRequest request) {
        findOrThrow(principal.getOrganizationId(), campaignId);

        boolean hasLead = request.leadId() != null;
        boolean hasContact = request.contactId() != null;
        if (hasLead == hasContact) {
            throw new BusinessException(
                    "CAMPAIGN_MEMBER_INVALID_TARGET", "A campaign member must reference exactly one of a lead or a contact", HttpStatus.BAD_REQUEST);
        }

        UUID organizationId = principal.getOrganizationId();
        if (hasLead) {
            if (!leadRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(request.leadId(), organizationId)) {
                throw new ResourceNotFoundException("Lead", request.leadId());
            }
            if (memberRepository.existsByCampaignIdAndLeadId(campaignId, request.leadId())) {
                throw new DuplicateResourceException("This lead is already a member of this campaign");
            }
        } else {
            if (!contactRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(request.contactId(), organizationId)) {
                throw new ResourceNotFoundException("Contact", request.contactId());
            }
            if (memberRepository.existsByCampaignIdAndContactId(campaignId, request.contactId())) {
                throw new DuplicateResourceException("This contact is already a member of this campaign");
            }
        }

        CampaignMember member = new CampaignMember(campaignId, request.leadId(), request.contactId());
        memberRepository.save(member);
        return member;
    }

    /** Moving to RESPONDED/CONVERTED stamps respondedAt; moving away from either clears it - see CampaignMember's javadoc. */
    @Transactional
    public CampaignMember updateMemberStatus(UserPrincipal principal, UUID campaignId, UUID memberId, CampaignMember.Status status) {
        findOrThrow(principal.getOrganizationId(), campaignId);
        CampaignMember member = memberRepository.findByIdAndCampaignId(memberId, campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("CampaignMember", memberId));

        member.setStatus(status);
        member.setRespondedAt(
                status == CampaignMember.Status.RESPONDED || status == CampaignMember.Status.CONVERTED ? Instant.now() : null);
        memberRepository.save(member);
        return member;
    }

    @Transactional
    public void removeMember(UserPrincipal principal, UUID campaignId, UUID memberId) {
        findOrThrow(principal.getOrganizationId(), campaignId);
        CampaignMember member = memberRepository.findByIdAndCampaignId(memberId, campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("CampaignMember", memberId));
        memberRepository.delete(member);
    }

    private Campaign findOrThrow(UUID organizationId, UUID campaignId) {
        return campaignRepository.findActiveByIdAndOrganizationId(campaignId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign", campaignId));
    }

    private void validateStatusTransition(Campaign.Status from, Campaign.Status to) {
        boolean valid = switch (to) {
            case ACTIVE -> from == Campaign.Status.PLANNED;
            case COMPLETED -> from == Campaign.Status.ACTIVE;
            case CANCELLED -> from == Campaign.Status.PLANNED || from == Campaign.Status.ACTIVE;
            case PLANNED -> false;
        };
        if (!valid) {
            throw new BusinessException(
                    "CAMPAIGN_INVALID_STATUS_TRANSITION", "Cannot move a campaign from " + from + " to " + to, HttpStatus.CONFLICT);
        }
    }

    private void applyFields(
            Campaign campaign, java.time.LocalDate startDate, java.time.LocalDate endDate,
            java.math.BigDecimal budget, java.math.BigDecimal actualCost, String description) {
        campaign.setStartDate(startDate);
        campaign.setEndDate(endDate);
        campaign.setBudget(budget);
        campaign.setActualCost(actualCost);
        campaign.setDescription(description);
    }
}
