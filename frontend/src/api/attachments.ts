import { apiClient, unwrap } from "../lib/apiClient";
import type { AssignOwnerRequest, AttachmentDto, CrmRecordType, PageResponse, UpdateAttachmentRequest } from "../types/api";

export interface ListAttachmentsParams {
  page?: number;
  size?: number;
  sort?: string;
  relatedToType?: CrmRecordType;
  relatedToId?: string;
}

export function listAttachments(params: ListAttachmentsParams = {}): Promise<PageResponse<AttachmentDto>> {
  return unwrap(apiClient.get("/api/v1/attachments", { params }));
}

export function getAttachment(attachmentId: string): Promise<AttachmentDto> {
  return unwrap(apiClient.get(`/api/v1/attachments/${attachmentId}`));
}

/** Multipart, unlike every other create call in this codebase - passing a FormData body lets axios set its own Content-Type (with the multipart boundary) rather than the JSON default every other api/*.ts function relies on. */
export function uploadAttachment(
  file: File,
  relatedToType: CrmRecordType,
  relatedToId: string,
  description?: string,
): Promise<AttachmentDto> {
  const formData = new FormData();
  formData.append("file", file);
  formData.append("relatedToType", relatedToType);
  formData.append("relatedToId", relatedToId);
  if (description) formData.append("description", description);
  return unwrap(apiClient.post("/api/v1/attachments", formData));
}

export function updateAttachment(attachmentId: string, request: UpdateAttachmentRequest): Promise<AttachmentDto> {
  return unwrap(apiClient.put(`/api/v1/attachments/${attachmentId}`, request));
}

export function deleteAttachment(attachmentId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/attachments/${attachmentId}`));
}

/** Not wired into any page yet, same as calendarEvents.ts's assignCalendarEventOwner and tickets.ts's - kept for API parity with the backend, UI to follow later. */
export function assignAttachmentOwner(attachmentId: string, request: AssignOwnerRequest): Promise<AttachmentDto> {
  return unwrap(apiClient.patch(`/api/v1/attachments/${attachmentId}/owner`, request));
}

/**
 * Bypasses unwrap() - GET /{id}/download returns the raw file bytes, not an ApiResponse<T>
 * JSON envelope every other endpoint uses, so there's no `.data.data` to unwrap and a failure
 * response body is a Blob (often containing JSON text) rather than already-parsed JSON, which
 * is why this doesn't attempt the usual ApiError field-error parsing on failure.
 */
export async function downloadAttachment(attachmentId: string): Promise<Blob> {
  try {
    const response = await apiClient.get<Blob>(`/api/v1/attachments/${attachmentId}/download`, { responseType: "blob" });
    return response.data;
  } catch {
    throw new Error("Could not download this file.");
  }
}
