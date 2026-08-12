package com.aitrainercrm.platform.attachment.service;

import com.aitrainercrm.platform.account.repository.AccountRepository;
import com.aitrainercrm.platform.attachment.dto.DownloadedFile;
import com.aitrainercrm.platform.attachment.dto.UpdateAttachmentRequest;
import com.aitrainercrm.platform.attachment.entity.Attachment;
import com.aitrainercrm.platform.attachment.repository.AttachmentRepository;
import com.aitrainercrm.platform.attachment.storage.FileStorageService;
import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.common.exception.BusinessException;
import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.common.util.CsvWriter;
import com.aitrainercrm.platform.contact.repository.ContactRepository;
import com.aitrainercrm.platform.lead.repository.LeadRepository;
import com.aitrainercrm.platform.opportunity.repository.OpportunityRepository;
import com.aitrainercrm.platform.role.entity.Permission;
import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.ticket.repository.TicketRepository;
import com.aitrainercrm.platform.user.repository.UserRepository;
import java.io.IOException;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Files uploaded against an Account/Contact/Opportunity/Lead/Ticket. Same shape as
 * {@code EmailMessageService}: OWN/TEAM/DEPARTMENT/ORGANIZATION record-level authorization via
 * {@link ScopeAuthorizationService}, {@code resolveOwner} defaulting a null {@code ownerId} to
 * the caller, {@code validateRelatedTo} borrowed from {@code ActivityService}'s pattern - plus
 * one extra step neither of those has: reading the uploaded bytes and handing them to
 * {@link FileStorageService} before the metadata row is even built, since {@code storageKey}
 * has to exist before the entity can be constructed.
 */
@Service
@RequiredArgsConstructor
public class AttachmentService {

    private static final Permission.Resource RESOURCE = Permission.Resource.ATTACHMENT;

    /** 20 MB - generous enough for a scanned contract or a screenshot, small enough that a single upload can't tie up the request thread for long on local disk. */
    private static final long MAX_FILE_SIZE_BYTES = 20L * 1024 * 1024;

    private final AttachmentRepository attachmentRepository;
    private final AccountRepository accountRepository;
    private final ContactRepository contactRepository;
    private final OpportunityRepository opportunityRepository;
    private final LeadRepository leadRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final ScopeAuthorizationService scopeAuthorizationService;
    private final FileStorageService fileStorageService;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<Attachment> list(
            UserPrincipal principal, Attachment.RelatedToType relatedToType, UUID relatedToId, Pageable pageable) {
        Optional<Set<UUID>> visibleOwnerIds = scopeAuthorizationService.visibleOwnerIds(principal, RESOURCE, Permission.Action.READ);
        UUID organizationId = principal.getOrganizationId();

        if (relatedToType != null && relatedToId != null) {
            return visibleOwnerIds
                    .map(ownerIds -> attachmentRepository.findByOrganizationIdAndOwnerIdInAndRelatedToTypeAndRelatedToIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                            organizationId, ownerIds, relatedToType, relatedToId, pageable))
                    .orElseGet(() -> attachmentRepository.findByOrganizationIdAndRelatedToTypeAndRelatedToIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                            organizationId, relatedToType, relatedToId, pageable));
        }

        return visibleOwnerIds
                .map(ownerIds -> attachmentRepository.findByOrganizationIdAndOwnerIdInAndDeletedAtIsNullOrderByCreatedAtDesc(organizationId, ownerIds, pageable))
                .orElseGet(() -> attachmentRepository.findByOrganizationIdAndDeletedAtIsNullOrderByCreatedAtDesc(organizationId, pageable));
    }

    @Transactional(readOnly = true)
    public Attachment get(UserPrincipal principal, UUID attachmentId) {
        Attachment attachment = findOrThrow(principal.getOrganizationId(), attachmentId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.READ, attachment.getOwnerId());
        return attachment;
    }

    @Transactional(readOnly = true)
    public DownloadedFile download(UserPrincipal principal, UUID attachmentId) {
        Attachment attachment = get(principal, attachmentId);
        byte[] content = fileStorageService.retrieve(attachment.getStorageKey());
        return new DownloadedFile(attachment.getFileName(), attachment.getContentType(), content);
    }

    /** Backs GET /attachments/export (ATTACHMENT:EXPORT) - metadata only, obviously; a CSV can't carry file bytes. Same shape as EmailMessageService#exportCsv. */
    @Transactional(readOnly = true)
    public byte[] exportCsv(UserPrincipal principal) {
        Optional<Set<UUID>> visibleOwnerIds = scopeAuthorizationService.visibleOwnerIds(principal, RESOURCE, Permission.Action.EXPORT);
        UUID organizationId = principal.getOrganizationId();
        List<Attachment> attachments = visibleOwnerIds
                .map(ownerIds -> attachmentRepository.findByOrganizationIdAndOwnerIdInAndDeletedAtIsNullOrderByCreatedAtDesc(organizationId, ownerIds))
                .orElseGet(() -> attachmentRepository.findByOrganizationIdAndDeletedAtIsNullOrderByCreatedAtDesc(organizationId));

        CsvWriter csv = new CsvWriter().row(
                "File Name", "Content Type", "Size (bytes)", "Related To Type", "Related To Id", "Description", "Created At");
        for (Attachment attachment : attachments) {
            csv.row(
                    attachment.getFileName(), attachment.getContentType(), attachment.getFileSizeBytes(),
                    attachment.getRelatedToType(), attachment.getRelatedToId(), attachment.getDescription(), attachment.getCreatedAt());
        }
        return csv.toBytes();
    }

    @Transactional
    public Attachment create(
            UserPrincipal principal, MultipartFile file, Attachment.RelatedToType relatedToType, UUID relatedToId,
            String description, UUID requestedOwnerId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("ATTACHMENT_EMPTY_FILE", "No file was uploaded", HttpStatus.BAD_REQUEST);
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new BusinessException("ATTACHMENT_TOO_LARGE", "File exceeds the 20 MB upload limit", HttpStatus.BAD_REQUEST);
        }

        UUID organizationId = principal.getOrganizationId();
        UUID ownerId = resolveOwner(principal, Permission.Action.CREATE, requestedOwnerId);
        validateRelatedTo(organizationId, relatedToType, relatedToId);

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException("ATTACHMENT_UNREADABLE", "Could not read the uploaded file", HttpStatus.BAD_REQUEST);
        }

        String originalFileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
        String storageKey = fileStorageService.store(organizationId, originalFileName, content);

        Attachment attachment = new Attachment(
                organizationId, relatedToType, relatedToId, originalFileName, file.getContentType(), content.length, storageKey, ownerId);
        attachment.setDescription(description);
        attachmentRepository.save(attachment);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), organizationId, "Attachment", attachment.getId()));
        return attachment;
    }

    @Transactional
    public Attachment update(UserPrincipal principal, UUID attachmentId, UpdateAttachmentRequest request) {
        Attachment attachment = findOrThrow(principal.getOrganizationId(), attachmentId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, attachment.getOwnerId());
        validateRelatedTo(principal.getOrganizationId(), request.relatedToType(), request.relatedToId());

        attachment.setFileName(request.fileName());
        attachment.setDescription(request.description());
        attachment.setRelatedToType(request.relatedToType());
        attachment.setRelatedToId(request.relatedToId());
        attachmentRepository.save(attachment);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "Attachment", attachment.getId()));
        return attachment;
    }

    /** Soft-deletes the metadata row only - the underlying file is left in storage, same reasoning nothing else in this schema hard-deletes on a plain DELETE (see V18's migration comment). */
    @Transactional
    public void delete(UserPrincipal principal, UUID attachmentId) {
        Attachment attachment = findOrThrow(principal.getOrganizationId(), attachmentId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.DELETE, attachment.getOwnerId());

        attachment.setDeletedAt(Instant.now());
        attachmentRepository.save(attachment);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "Attachment", attachmentId));
    }

    @Transactional
    public Attachment assignOwner(UserPrincipal principal, UUID attachmentId, UUID newOwnerId) {
        Attachment attachment = findOrThrow(principal.getOrganizationId(), attachmentId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.ASSIGN, attachment.getOwnerId());
        assertUserInOrganization(principal.getOrganizationId(), newOwnerId);

        attachment.setOwnerId(newOwnerId);
        attachmentRepository.save(attachment);

        events.publishEvent(new CrmAuditEvents.RecordAssigned(principal.getId(), principal.getOrganizationId(), "Attachment", attachment.getId(), newOwnerId));
        return attachment;
    }

    private Attachment findOrThrow(UUID organizationId, UUID attachmentId) {
        return attachmentRepository.findActiveByIdAndOrganizationId(attachmentId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment", attachmentId));
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

    /** No FK to lean on (see V18's migration comment) - checks the reference exists, in this tenant, against whichever of the five repositories relatedToType names. Required, unlike CalendarEventService's optional version - see Attachment's javadoc. */
    private void validateRelatedTo(UUID organizationId, Attachment.RelatedToType relatedToType, UUID relatedToId) {
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
