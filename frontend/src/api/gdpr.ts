import axios from "axios";
import { ApiError, apiClient, unwrap } from "../lib/apiClient";
import type { ApiErrorBody, CreateDataSubjectRequest, DataSubjectRequestDto, PageResponse } from "../types/api";

export interface ListParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listDataSubjectRequests(params: ListParams = {}): Promise<PageResponse<DataSubjectRequestDto>> {
  return unwrap(apiClient.get("/api/v1/data-subject-requests", { params }));
}

export function getDataSubjectRequest(requestId: string): Promise<DataSubjectRequestDto> {
  return unwrap(apiClient.get(`/api/v1/data-subject-requests/${requestId}`));
}

/**
 * POST /export returns a raw JSON file (`ResponseEntity<byte[]>` on the backend, not the usual
 * `ApiResponse` envelope) - same download-via-throwaway-anchor technique `exportEntities` in
 * api/importExport.ts uses for CSV, just a POST with a request body instead of a GET, and
 * `data-subject-export.json` instead of a per-entity CSV filename. See that function's javadoc for
 * why `responseType: "blob"` needs its own error-recovery path: a 403 would otherwise come back as
 * an unreadable Blob instead of the usual JSON error body.
 */
export async function exportDataSubject(request: CreateDataSubjectRequest): Promise<void> {
  try {
    const response = await apiClient.post("/api/v1/data-subject-requests/export", request, { responseType: "blob" });
    const url = URL.createObjectURL(response.data as Blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = "data-subject-export.json";
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    URL.revokeObjectURL(url);
  } catch (error) {
    if (axios.isAxiosError(error) && error.response?.data instanceof Blob) {
      const text = await error.response.data.text();
      let body: ApiErrorBody | null = null;
      try {
        body = JSON.parse(text) as ApiErrorBody;
      } catch {
        // Not JSON - fall through to the generic message below.
      }
      throw new ApiError(body ?? { message: "Could not export this data." }, error.response.status);
    }
    throw error;
  }
}

export function eraseDataSubject(request: CreateDataSubjectRequest): Promise<DataSubjectRequestDto> {
  return unwrap(apiClient.post("/api/v1/data-subject-requests/erase", request));
}
