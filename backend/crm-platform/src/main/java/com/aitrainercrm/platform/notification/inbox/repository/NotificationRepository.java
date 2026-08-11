package com.aitrainercrm.platform.notification.inbox.repository;

import com.aitrainercrm.platform.notification.inbox.entity.Notification;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /** Every finder here is scoped by recipientUserId, not just organizationId - see Notification's javadoc for why there's no ScopeAuthorizationService widening this the way TicketRepository's owner-set lookups do. */
    @Query("select n from Notification n where n.id = :id and n.organizationId = :organizationId and n.recipientUserId = :recipientUserId")
    Optional<Notification> findOwnById(@Param("id") UUID id, @Param("organizationId") UUID organizationId, @Param("recipientUserId") UUID recipientUserId);

    Page<Notification> findByOrganizationIdAndRecipientUserIdOrderByCreatedAtDesc(UUID organizationId, UUID recipientUserId, Pageable pageable);

    Page<Notification> findByOrganizationIdAndRecipientUserIdAndReadAtIsNullOrderByCreatedAtDesc(
            UUID organizationId, UUID recipientUserId, Pageable pageable);

    long countByOrganizationIdAndRecipientUserIdAndReadAtIsNull(UUID organizationId, UUID recipientUserId);

    /** Bulk update rather than load-N-then-save-N - same shape as RefreshTokenRepository#revokeAllForUser. */
    @Modifying
    @Query("update Notification n set n.readAt = :readAt where n.organizationId = :organizationId and n.recipientUserId = :recipientUserId and n.readAt is null")
    int markAllRead(@Param("organizationId") UUID organizationId, @Param("recipientUserId") UUID recipientUserId, @Param("readAt") Instant readAt);
}
