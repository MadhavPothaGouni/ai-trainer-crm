package com.aitrainercrm.platform.notification.inbox.dto;

/** Backs the nav-badge endpoint - deliberately its own tiny record rather than reusing NotificationDto/PageResponse for a single number. */
public record UnreadCountResponse(long unreadCount) {
}
