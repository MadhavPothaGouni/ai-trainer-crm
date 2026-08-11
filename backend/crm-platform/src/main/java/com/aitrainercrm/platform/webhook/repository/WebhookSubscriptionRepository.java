package com.aitrainercrm.platform.webhook.repository;

import com.aitrainercrm.platform.webhook.entity.WebhookSubscription;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WebhookSubscriptionRepository extends JpaRepository<WebhookSubscription, UUID> {

    @Query("select w from WebhookSubscription w where w.id = :id and w.organizationId = :organizationId")
    Optional<WebhookSubscription> findByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    Page<WebhookSubscription> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId, Pageable pageable);

    /** Every active subscription for the org - filtered further in-memory by {@link WebhookSubscription#matches}, since matching is "null eventType OR exact match," not a plain equality the database can do alone without an extra OR-null clause on every dispatch. */
    List<WebhookSubscription> findByOrganizationIdAndActiveTrue(UUID organizationId);
}
