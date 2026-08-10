import { apiClient, unwrap } from "../lib/apiClient";
import type {
  AssignOwnerRequest,
  ConvertLeadRequest,
  CreateLeadRequest,
  LeadConversionResult,
  LeadDto,
  PageResponse,
  UpdateLeadRequest,
  UpdateLeadStatusRequest,
} from "../types/api";

export interface ListLeadsParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listLeads(params: ListLeadsParams = {}): Promise<PageResponse<LeadDto>> {
  return unwrap(apiClient.get("/api/v1/leads", { params }));
}

export function getLead(leadId: string): Promise<LeadDto> {
  return unwrap(apiClient.get(`/api/v1/leads/${leadId}`));
}

export function createLead(request: CreateLeadRequest): Promise<LeadDto> {
  return unwrap(apiClient.post("/api/v1/leads", request));
}

export function updateLead(leadId: string, request: UpdateLeadRequest): Promise<LeadDto> {
  return unwrap(apiClient.put(`/api/v1/leads/${leadId}`, request));
}

export function updateLeadStatus(leadId: string, request: UpdateLeadStatusRequest): Promise<LeadDto> {
  return unwrap(apiClient.patch(`/api/v1/leads/${leadId}/status`, request));
}

export function deleteLead(leadId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/leads/${leadId}`));
}

export function assignLeadOwner(leadId: string, request: AssignOwnerRequest): Promise<LeadDto> {
  return unwrap(apiClient.patch(`/api/v1/leads/${leadId}/owner`, request));
}

export function convertLead(leadId: string, request: ConvertLeadRequest): Promise<LeadConversionResult> {
  return unwrap(apiClient.post(`/api/v1/leads/${leadId}/convert`, request));
}
