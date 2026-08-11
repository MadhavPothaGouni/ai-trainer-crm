package com.aitrainercrm.platform.notification.inbox.service;

import com.aitrainercrm.platform.account.repository.AccountRepository;
import com.aitrainercrm.platform.common.exception.BusinessException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.contact.repository.ContactRepository;
import com.aitrainercrm.platform.lead.repository.LeadRepository;
import com.aitrainercrm.platform.notification.inbox.dto.CreateNotificationRequest;
import com.aitrainercrm.platform.notification.inbox.entity.Notification;
import com.aitrainercrm.platform.notification.inbox.repository.NotificationRepository;
import com.aitrainercrm.platform.opportunity.repository.OpportunityRepository;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.ticket.repository.TicketRepository;
import com.aitrainercrm.platform.user.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A teammate's own notification inbox. See {@link Notification}'s javadoc
 * and V17's migration comment for why this is a third, simpler access
 * pattern than every module since Ticket (V14) - no {@code
 * Permission.Resource}, no {@code ScopeAuthorizationService}, no
 * {@code @PreAuthorize} scope ladder on the controller. Every method here
 * takes the caller's own id as the only filter that matters; {@code
 * findOwnOrThrow} folds "does this row exist" and "is it mine" into one
 * repository call ({@code NotificationRepository#findOwnById}) so a
 * notification belonging to someone else in the same org 404s exactly like
 * one that doesn't exist at all, rather than 403ing and confirming its
 * existence.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final AccountRepository accountRepository;
    private final ContactRepository contactRepository;
    private final OpportunityRepository opportunityRepository;
    private final LeadRepository leadRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Page<Notification> list(UserPrincipal principal, boolean unreadOnly, Pageable pageable) {
        UUID organizationId = principal.getOrganizationId();
        return unreadOnly
                ? notificationRepository.findByOrganizationIdAndRecipientUserIdAndReadAtIsNullOrderByCreatedAtDesc(organizationId, principal.getId(), pageable)
                : notificationRepository.findByOrganizationIdAndRecipientUserIdOrderByCreatedAtDesc(organizationId, principal.getId(), pageable);
    }

    @Transactional(readOnly = true)
    public long unreadCount(UserPrincipal principal) {
        return notificationRepository.countByOrganizationIdAndRecipientUserIdAndReadAtIsNull(principal.getOrganizationId(), principal.getId());
    }

    /** Any authenticated org member can send a notification to any other org member - there's no permission gate here, same reasoning leaving an Activity note or a Campaign comment isn't gated beyond "you're in this org." */
    @Transactional
    public Notification create(UserPrincipal principal, CreateNotificationRequest request) {
        UUID organizationId = principal.getOrganizationId();
        assertUserInOrganization(organizationId, request.recipientUserId());
        validateRelatedTo(organizationId, request.relatedToType(), request.relatedToId());

        Notification notification = new Notification(organizationId, request.recipientUserId(), request.type(), request.title());
        notification.setSenderUserId(principal.getId());
        notification.setBody(request.body());
        notification.setRelatedToType(request.relatedToType());
        notification.setRelatedToId(request.relatedToId());
        notificationRepository.save(notification);
        return notification;
    }

    @Transactional
    public Notification markRead(UserPrincipal principal, UUID notificationId) {
        Notification notification = findOwnOrThrow(principal, notificationId);
        if (!notification.isRead()) {
            notification.setReadAt(Instant.now());
            notificationRepository.save(notification);
        }
        return notification;
    }

    /** @return how many were flipped from unread to read - lets the frontend skip a refetch when it's already 0. */
    @Transactional
    public int markAllRead(UserPrincipal principal) {
        return notificationRepository.markAllRead(principal.getOrganizationId(), principal.getId(), Instant.now());
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID notificationId) {
        Notification notification = findOwnOrThrow(principal, notificationId);
        notificationRepository.delete(notification);
    }

    private Notification findOwnOrThrow(UserPrincipal principal, UUID notificationId) {
        return notificationRepository.findOwnById(notificationId, principal.getOrganizationId(), principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Notification", notificationId));
    }

    private void assertUserInOrganization(UUID organizationId, UUID userId) {
        boolean exists = userRepository.findActiveById(userId).map(u -> organizationId.equals(u.getOrganizationId())).orElse(false);
        if (!exists) {
            throw new ResourceNotFoundException("User", userId);
        }
    }

    /** Both-null-or-both-set, same shape as CalendarEventService#validateRelatedTo - a GENERAL announcement has no single record to deep-link to. */
    private void validateRelatedTo(UUID organizationId, Notification.RelatedToType relatedToType, UUID relatedToId) {
        boolean hasType = relatedToType != null;
        boolean hasId = relatedToId != null;
        if (hasType != hasId) {
            throw new BusinessException(
                    "NOTIFICATION_INVALID_RELATED_TO", "relatedToType and relatedToId must both be set or both be omitted", HttpStatus.BAD_REQUEST);
        }
        if (!hasType) {
            return;
        }
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
