import { apiClient, unwrap } from "../lib/apiClient";
import type {
  AddCampaignMemberRequest,
  CampaignDto,
  CampaignMemberDto,
  CampaignStatsDto,
  CreateCampaignRequest,
  PageResponse,
  UpdateCampaignMemberStatusRequest,
  UpdateCampaignRequest,
  UpdateCampaignStatusRequest,
} from "../types/api";

export interface ListCampaignsParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listCampaigns(params: ListCampaignsParams = {}): Promise<PageResponse<CampaignDto>> {
  return unwrap(apiClient.get("/api/v1/campaigns", { params }));
}

export function getCampaign(campaignId: string): Promise<CampaignDto> {
  return unwrap(apiClient.get(`/api/v1/campaigns/${campaignId}`));
}

export function getCampaignStats(campaignId: string): Promise<CampaignStatsDto> {
  return unwrap(apiClient.get(`/api/v1/campaigns/${campaignId}/stats`));
}

export function createCampaign(request: CreateCampaignRequest): Promise<CampaignDto> {
  return unwrap(apiClient.post("/api/v1/campaigns", request));
}

export function updateCampaign(campaignId: string, request: UpdateCampaignRequest): Promise<CampaignDto> {
  return unwrap(apiClient.put(`/api/v1/campaigns/${campaignId}`, request));
}

export function updateCampaignStatus(campaignId: string, request: UpdateCampaignStatusRequest): Promise<CampaignDto> {
  return unwrap(apiClient.patch(`/api/v1/campaigns/${campaignId}/status`, request));
}

export function deleteCampaign(campaignId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/campaigns/${campaignId}`));
}

export function listCampaignMembers(campaignId: string): Promise<CampaignMemberDto[]> {
  return unwrap(apiClient.get(`/api/v1/campaigns/${campaignId}/members`));
}

export function addCampaignMember(campaignId: string, request: AddCampaignMemberRequest): Promise<CampaignMemberDto> {
  return unwrap(apiClient.post(`/api/v1/campaigns/${campaignId}/members`, request));
}

export function updateCampaignMemberStatus(
  campaignId: string,
  memberId: string,
  request: UpdateCampaignMemberStatusRequest,
): Promise<CampaignMemberDto> {
  return unwrap(apiClient.patch(`/api/v1/campaigns/${campaignId}/members/${memberId}/status`, request));
}

export function removeCampaignMember(campaignId: string, memberId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/campaigns/${campaignId}/members/${memberId}`));
}

/** CAMPAIGN:EXPORT - downloads a CSV of every campaign in the org. Bypasses `unwrap` (the response is a raw file, not an ApiResponse envelope) and triggers a browser download directly. */
export async function exportCampaignsCsv(): Promise<void> {
  const response = await apiClient.get("/api/v1/campaigns/export", { responseType: "blob" });
  downloadBlob(response.data as Blob, "campaigns.csv");
}

function downloadBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}
