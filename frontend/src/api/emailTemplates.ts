import { apiClient, unwrap } from "../lib/apiClient";
import type {
  CreateEmailTemplateRequest,
  EmailTemplateCategory,
  EmailTemplateDto,
  PageResponse,
  RenderEmailTemplateRequest,
  RenderedEmailDto,
  UpdateEmailTemplateRequest,
} from "../types/api";

export interface ListEmailTemplatesParams {
  page?: number;
  size?: number;
  sort?: string;
  category?: EmailTemplateCategory;
}

export function listEmailTemplates(params: ListEmailTemplatesParams = {}): Promise<PageResponse<EmailTemplateDto>> {
  return unwrap(apiClient.get("/api/v1/email-templates", { params }));
}

export function getEmailTemplate(templateId: string): Promise<EmailTemplateDto> {
  return unwrap(apiClient.get(`/api/v1/email-templates/${templateId}`));
}

export function createEmailTemplate(request: CreateEmailTemplateRequest): Promise<EmailTemplateDto> {
  return unwrap(apiClient.post("/api/v1/email-templates", request));
}

export function updateEmailTemplate(templateId: string, request: UpdateEmailTemplateRequest): Promise<EmailTemplateDto> {
  return unwrap(apiClient.put(`/api/v1/email-templates/${templateId}`, request));
}

export function deleteEmailTemplate(templateId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/email-templates/${templateId}`));
}

/** Read-only preview - nothing is persisted. Safe to call on every keystroke of the target picker. */
export function renderEmailTemplate(templateId: string, request: RenderEmailTemplateRequest): Promise<RenderedEmailDto> {
  return unwrap(apiClient.post(`/api/v1/email-templates/${templateId}/render`, request));
}
