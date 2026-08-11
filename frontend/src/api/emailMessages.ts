import { apiClient, unwrap } from "../lib/apiClient";
import type { AssignOwnerRequest, CrmRecordType, EmailMessageDto, LogEmailRequest, PageResponse } from "../types/api";

export interface ListEmailMessagesParams {
  page?: number;
  size?: number;
  sort?: string;
  relatedToType?: CrmRecordType;
  relatedToId?: string;
}

export function listEmailMessages(params: ListEmailMessagesParams = {}): Promise<PageResponse<EmailMessageDto>> {
  return unwrap(apiClient.get("/api/v1/email-messages", { params }));
}

export function getEmailMessage(emailId: string): Promise<EmailMessageDto> {
  return unwrap(apiClient.get(`/api/v1/email-messages/${emailId}`));
}

export function logEmailMessage(request: LogEmailRequest): Promise<EmailMessageDto> {
  return unwrap(apiClient.post("/api/v1/email-messages", request));
}

export function updateEmailMessage(emailId: string, request: LogEmailRequest): Promise<EmailMessageDto> {
  return unwrap(apiClient.put(`/api/v1/email-messages/${emailId}`, request));
}

export function deleteEmailMessage(emailId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/email-messages/${emailId}`));
}

export function assignEmailMessageOwner(emailId: string, request: AssignOwnerRequest): Promise<EmailMessageDto> {
  return unwrap(apiClient.patch(`/api/v1/email-messages/${emailId}/owner`, request));
}
