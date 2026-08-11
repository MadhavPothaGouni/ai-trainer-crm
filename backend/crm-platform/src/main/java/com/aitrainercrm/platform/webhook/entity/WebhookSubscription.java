package com.aitrainercrm.platform.webhook.entity;

import com.aitrainercrm.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A subscriber's endpoint plus the shared secret this platform signs every
 * delivery to it with (HMAC-SHA256, see {@code WebhookDispatchListener}).
 * {@code eventType} is nullable - {@code null} subscribes to every
 * {@code CrmAuditEvents.RecordCreated/RecordUpdated/RecordDeleted/RecordAssigned}
 * the organization generates, a non-null value (e.g. {@code "Opportunity_CREATED"})
 * subscribes to exactly one. See V7's migration comment for why
 * {@code secret} is stored in plaintext here but {@code ApiKey#hashedSecret}
 * isn't.
 */
@Entity
@Table(name = "webhook_subscriptions")
@Getter
@Setter
@NoArgsConstructor
public class WebhookSubscription extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 500)
    private String url;

    @Column(name = "event_type", length = 100)
    private String eventType;

    @Column(nullable = false, length = 255)
    private String secret;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    @Column(name = "last_triggered_at")
    private Instant lastTriggeredAt;

    @Column(name = "last_response_status")
    private Integer lastResponseStatus;

    public WebhookSubscription(UUID organizationId, String url, String eventType, String secret, UUID createdByUserId) {
        this.organizationId = organizationId;
        this.url = url;
        this.eventType = eventType;
        this.secret = secret;
        this.createdByUserId = createdByUserId;
    }

    /** {@code true} if this subscription wants to hear about {@code action} (e.g. {@code "Opportunity_CREATED"}) - null eventType means "everything." */
    public boolean matches(String action) {
        return active && (eventType == null || eventType.isBlank() || eventType.equals(action));
    }
}
