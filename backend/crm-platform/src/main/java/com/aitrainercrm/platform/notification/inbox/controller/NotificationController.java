package com.aitrainercrm.platform.notification.inbox.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.notification.inbox.dto.CreateNotificationRequest;
import com.aitrainercrm.platform.notification.inbox.dto.NotificationDto;
import com.aitrainercrm.platform.notification.inbox.dto.UnreadCountResponse;
import com.aitrainercrm.platform.notification.inbox.entity.Notification;
import com.aitrainercrm.platform.notification.inbox.service.NotificationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * No {@code @PreAuthorize} on any method here - see NotificationService's
 * javadoc for why. Every endpoint only needs the standing
 * {@code isAuthenticated()} check the security filter chain already applies
 * to every {@code /api/v1/**} route; the org+recipient scoping happens
 * entirely inside the service.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ApiResponse<PageResponse<NotificationDto>> list(
            @RequestParam(required = false, defaultValue = "false") boolean unreadOnly,
            Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal) {
        Page<Notification> page = notificationService.list(principal, unreadOnly, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(NotificationDto::from).toList()));
    }

    @GetMapping("/unread-count")
    public ApiResponse<UnreadCountResponse> unreadCount(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(new UnreadCountResponse(notificationService.unreadCount(principal)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<NotificationDto> create(@Valid @RequestBody CreateNotificationRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(NotificationDto.from(notificationService.create(principal, request)), "Notification sent");
    }

    @PatchMapping("/{notificationId}/read")
    public ApiResponse<NotificationDto> markRead(@PathVariable UUID notificationId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(NotificationDto.from(notificationService.markRead(principal, notificationId)), "Marked read");
    }

    @PatchMapping("/read-all")
    public ApiResponse<Void> markAllRead(@AuthenticationPrincipal UserPrincipal principal) {
        int count = notificationService.markAllRead(principal);
        return ApiResponse.ok(null, count + " notification(s) marked read");
    }

    @DeleteMapping("/{notificationId}")
    public ApiResponse<Void> delete(@PathVariable UUID notificationId, @AuthenticationPrincipal UserPrincipal principal) {
        notificationService.delete(principal, notificationId);
        return ApiResponse.ok(null, "Notification deleted");
    }
}
