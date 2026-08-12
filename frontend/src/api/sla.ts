import { apiClient, unwrap } from "../lib/apiClient";
import type { CreateSlaPolicyRequest, PageResponse, SlaPolicyDto, TicketSlaStatusDto, UpdateSlaPolicyRequest } from "../types/api";

export interface ListSlaPoliciesParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listSlaPolicies(params: ListSlaPoliciesParams = {}): Promise<PageResponse<SlaPolicyDto>> {
  return unwrap(apiClient.get("/api/v1/sla-policies", { params }));
}

export function getSlaPolicy(policyId: string): Promise<SlaPolicyDto> {
  return unwrap(apiClient.get(`/api/v1/sla-policies/${policyId}`));
}

export function createSlaPolicy(request: CreateSlaPolicyRequest): Promise<SlaPolicyDto> {
  return unwrap(apiClient.post("/api/v1/sla-policies", request));
}

export function updateSlaPolicy(policyId: string, request: UpdateSlaPolicyRequest): Promise<SlaPolicyDto> {
  return unwrap(apiClient.put(`/api/v1/sla-policies/${policyId}`, request));
}

export function deleteSlaPolicy(policyId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/sla-policies/${policyId}`));
}

/** Returns null when no active policy covers the ticket's current priority - not an error, see TicketSlaStatusDto's javadoc in types/api.ts. */
export function getTicketSlaStatus(ticketId: string): Promise<TicketSlaStatusDto | null> {
  return unwrap(apiClient.get(`/api/v1/ticket-sla/${ticketId}`));
}
