package com.aitrainercrm.platform.webhook.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.webhook.dto.CreateWebhookSubscriptionRequest;
import com.aitrainercrm.platform.webhook.dto.UpdateWebhookSubscriptionRequest;
import com.aitrainercrm.platform.webhook.dto.WebhookSubscriptionDto;
import com.aitrainercrm.platform.webhook.entity.WebhookSubscription;
import com.aitrainercrm.platform.webhook.service.WebhookSubscriptionService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * There's no dedicated WEBHOOK permission resource in the catalog (see
 * V7's migration comment) - this rides on INTEGRATION, seeded
 * ORGANIZATION-scope-only in V2, so every gate here is a single authority
 * rather than the usual scope list.
 */
@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
public class WebhookSubscriptionController {

    private final WebhookSubscriptionService webhookSubscriptionService;

    @GetMapping
    @PreAuthorize("hasAuthority('INTEGRATION:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<WebhookSubscriptionDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<WebhookSubscription> page = webhookSubscriptionService.list(principal, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(WebhookSubscriptionDto::from).toList()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('INTEGRATION:CREATE:ORGANIZATION')")
    public ApiResponse<WebhookSubscriptionDto> create(
            @Valid @RequestBody CreateWebhookSubscriptionRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(
                WebhookSubscriptionDto.from(webhookSubscriptionService.create(principal, request)), "Webhook subscription created");
    }

    @PutMapping("/{webhookId}")
    @PreAuthorize("hasAuthority('INTEGRATION:UPDATE:ORGANIZATION')")
    public ApiResponse<WebhookSubscriptionDto> update(
            @PathVariable UUID webhookId,
            @Valid @RequestBody UpdateWebhookSubscriptionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(
                WebhookSubscriptionDto.from(webhookSubscriptionService.update(principal, webhookId, request)), "Webhook subscription updated");
    }

    @DeleteMapping("/{webhookId}")
    @PreAuthorize("hasAuthority('INTEGRATION:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID webhookId, @AuthenticationPrincipal UserPrincipal principal) {
        webhookSubscriptionService.delete(principal, webhookId);
        return ApiResponse.ok(null, "Webhook subscription deleted");
    }
}
