package com.aitrainercrm.platform.webhook.listener;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.webhook.entity.WebhookSubscription;
import com.aitrainercrm.platform.webhook.repository.WebhookSubscriptionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * The webhook counterpart to {@link com.aitrainercrm.platform.audit.listener.AuditEventListener}:
 * a second, entirely independent {@code @EventListener} on the exact same
 * {@link CrmAuditEvents} the audit module already consumes. Account/
 * Contact/Opportunity/Lead/Activity/Product/Quote services publish these
 * events with no idea this listener - or the audit log - exists; that's
 * the point of the domain-event pattern this platform uses everywhere.
 *
 * <p>Runs {@code @Async} for the same reason the audit listener does: a
 * slow or unreachable subscriber endpoint must never make the request that
 * triggered the event (creating an opportunity, say) slower or fail
 * because of it. Delivery is fire-and-forget - one attempt, a short
 * timeout, and the outcome recorded on the subscription
 * ({@code lastTriggeredAt}/{@code lastResponseStatus}) for the admin to
 * see in the webhooks list, not retried. A retry-with-backoff queue is a
 * real gap for a production webhook system and is called out as a scope
 * trim in the root README's Roadmap, not an oversight.
 *
 * <p>Every delivery is signed: {@code X-Webhook-Signature: sha256=<hex>}
 * is an HMAC-SHA256 of the raw JSON body using the subscription's own
 * secret, the same verify-on-receipt pattern Stripe/GitHub webhooks use -
 * so a subscriber can confirm a payload actually came from this platform
 * and wasn't forged by whoever guessed their URL.
 */
@Component
@RequiredArgsConstructor
public class WebhookDispatchListener {

    private static final Logger log = LoggerFactory.getLogger(WebhookDispatchListener.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private final WebhookSubscriptionRepository webhookSubscriptionRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build();

    @Async
    @EventListener
    public void onRecordCreated(CrmAuditEvents.RecordCreated event) {
        dispatch(event.organizationId(), event.resourceType() + "_CREATED", event.resourceType(), event.resourceId(), event.actorUserId());
    }

    @Async
    @EventListener
    public void onRecordUpdated(CrmAuditEvents.RecordUpdated event) {
        dispatch(event.organizationId(), event.resourceType() + "_UPDATED", event.resourceType(), event.resourceId(), event.actorUserId());
    }

    @Async
    @EventListener
    public void onRecordDeleted(CrmAuditEvents.RecordDeleted event) {
        dispatch(event.organizationId(), event.resourceType() + "_DELETED", event.resourceType(), event.resourceId(), event.actorUserId());
    }

    @Async
    @EventListener
    public void onRecordAssigned(CrmAuditEvents.RecordAssigned event) {
        dispatch(event.organizationId(), event.resourceType() + "_ASSIGNED", event.resourceType(), event.resourceId(), event.actorUserId());
    }

    private void dispatch(UUID organizationId, String action, String resourceType, UUID resourceId, UUID actorUserId) {
        List<WebhookSubscription> subscriptions = webhookSubscriptionRepository.findByOrganizationIdAndActiveTrue(organizationId);
        if (subscriptions.isEmpty()) return;

        String body = buildPayload(action, resourceType, resourceId, actorUserId);
        for (WebhookSubscription subscription : subscriptions) {
            if (subscription.matches(action)) {
                deliver(subscription, body);
            }
        }
    }

    private String buildPayload(String action, String resourceType, UUID resourceId, UUID actorUserId) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "event", action,
                    "resourceType", resourceType,
                    "resourceId", resourceId.toString(),
                    "actorUserId", actorUserId == null ? "" : actorUserId.toString(),
                    "timestamp", Instant.now().toString()));
        } catch (Exception e) {
            log.warn("Could not serialize webhook payload for {}/{}", resourceType, resourceId, e);
            return "{}";
        }
    }

    private void deliver(WebhookSubscription subscription, String body) {
        Integer responseStatus = null;
        try {
            String signature = sign(subscription.getSecret(), body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(subscription.getUrl()))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("X-Webhook-Signature", "sha256=" + signature)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            responseStatus = response.statusCode();
        } catch (Exception e) {
            log.warn("Webhook delivery to {} failed: {}", subscription.getUrl(), e.toString());
        } finally {
            subscription.setLastTriggeredAt(Instant.now());
            subscription.setLastResponseStatus(responseStatus);
            webhookSubscriptionRepository.save(subscription);
        }
    }

    private String sign(String secret, String body) throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
        byte[] signature = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(signature);
    }
}
