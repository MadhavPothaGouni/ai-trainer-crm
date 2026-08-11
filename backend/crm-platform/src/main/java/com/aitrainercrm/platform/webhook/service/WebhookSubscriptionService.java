package com.aitrainercrm.platform.webhook.service;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.webhook.dto.CreateWebhookSubscriptionRequest;
import com.aitrainercrm.platform.webhook.dto.UpdateWebhookSubscriptionRequest;
import com.aitrainercrm.platform.webhook.entity.WebhookSubscription;
import com.aitrainercrm.platform.webhook.repository.WebhookSubscriptionRepository;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD for webhook subscriptions - dispatch itself lives in
 * {@code WebhookDispatchListener}, which this service has no dependency on
 * (and vice versa); the two only communicate through the
 * {@code webhook_subscriptions} table and the domain-event bus, the same
 * decoupling every audit-consuming listener in this platform already uses.
 * Gated entirely by INTEGRATION:*:ORGANIZATION - see the controller's
 * javadoc for why there's no OWN/TEAM/DEPARTMENT variant.
 */
@Service
@RequiredArgsConstructor
public class WebhookSubscriptionService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final WebhookSubscriptionRepository webhookSubscriptionRepository;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<WebhookSubscription> list(UserPrincipal principal, Pageable pageable) {
        return webhookSubscriptionRepository.findByOrganizationIdOrderByCreatedAtDesc(principal.getOrganizationId(), pageable);
    }

    @Transactional
    public WebhookSubscription create(UserPrincipal principal, CreateWebhookSubscriptionRequest request) {
        String secret = "whsec_" + randomUrlSafeToken(32);
        WebhookSubscription subscription = new WebhookSubscription(
                principal.getOrganizationId(), request.url(), blankToNull(request.eventType()), secret, principal.getId());
        webhookSubscriptionRepository.save(subscription);

        events.publishEvent(new CrmAuditEvents.RecordCreated(
                principal.getId(), principal.getOrganizationId(), "WebhookSubscription", subscription.getId()));
        return subscription;
    }

    @Transactional
    public WebhookSubscription update(UserPrincipal principal, UUID webhookId, UpdateWebhookSubscriptionRequest request) {
        WebhookSubscription subscription = findOrThrow(principal.getOrganizationId(), webhookId);
        subscription.setUrl(request.url());
        subscription.setEventType(blankToNull(request.eventType()));
        subscription.setActive(request.active());
        webhookSubscriptionRepository.save(subscription);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(
                principal.getId(), principal.getOrganizationId(), "WebhookSubscription", subscription.getId()));
        return subscription;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID webhookId) {
        WebhookSubscription subscription = findOrThrow(principal.getOrganizationId(), webhookId);
        webhookSubscriptionRepository.delete(subscription);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(
                principal.getId(), principal.getOrganizationId(), "WebhookSubscription", webhookId));
    }

    private WebhookSubscription findOrThrow(UUID organizationId, UUID webhookId) {
        return webhookSubscriptionRepository
                .findByIdAndOrganizationId(webhookId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("WebhookSubscription", webhookId));
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private String randomUrlSafeToken(int byteLength) {
        byte[] bytes = new byte[byteLength];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
