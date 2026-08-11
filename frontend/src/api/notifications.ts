import { apiClient, unwrap } from "../lib/apiClient";
import type { CreateNotificationRequest, NotificationDto, PageResponse, UnreadCountResponse } from "../types/api";

export interface ListNotificationsParams {
  page?: number;
  size?: number;
  sort?: string;
  unreadOnly?: boolean;
}

export function listNotifications(params: ListNotificationsParams = {}): Promise<PageResponse<NotificationDto>> {
  return unwrap(apiClient.get("/api/v1/notifications", { params }));
}

export function getUnreadCount(): Promise<UnreadCountResponse> {
  return unwrap(apiClient.get("/api/v1/notifications/unread-count"));
}

export function createNotification(request: CreateNotificationRequest): Promise<NotificationDto> {
  return unwrap(apiClient.post("/api/v1/notifications", request));
}

export function markNotificationRead(notificationId: string): Promise<NotificationDto> {
  return unwrap(apiClient.patch(`/api/v1/notifications/${notificationId}/read`));
}

export function markAllNotificationsRead(): Promise<null> {
  return unwrap(apiClient.patch("/api/v1/notifications/read-all"));
}

export function deleteNotification(notificationId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/notifications/${notificationId}`));
}
