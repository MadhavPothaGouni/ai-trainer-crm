import axios from "axios";
import { ApiError, apiClient, unwrap } from "../lib/apiClient";
import type { ApiErrorBody, ImportEntityType, ImportJobDto, PageResponse } from "../types/api";

export interface ListParams {
  page?: number;
  size?: number;
  sort?: string;
}

const EXPORT_PATH: Record<ImportEntityType, string> = {
  ACCOUNT: "/api/v1/accounts/export",
  CONTACT: "/api/v1/contacts/export",
  LEAD: "/api/v1/leads/export",
};

const IMPORT_PATH: Record<ImportEntityType, string> = {
  ACCOUNT: "/api/v1/accounts/import",
  CONTACT: "/api/v1/contacts/import",
  LEAD: "/api/v1/leads/import",
};

const EXPORT_FILENAME: Record<ImportEntityType, string> = {
  ACCOUNT: "accounts.csv",
  CONTACT: "contacts.csv",
  LEAD: "leads.csv",
};

/**
 * Export endpoints return a raw CSV file (`ResponseEntity<byte[]>` on the backend, not the usual
 * `ApiResponse` envelope - see `ImportExportController`'s javadoc for why), so this bypasses
 * `unwrap` entirely and instead triggers a normal browser file download via a throwaway anchor
 * element and an object URL, same technique any "download this report" button uses.
 *
 * <p>`responseType: "blob"` applies to error responses too, not just successful ones - a 403 from
 * `@PreAuthorize` would otherwise come back as an unreadable `Blob` instead of the usual JSON error
 * body, so a failed export reads as "something unexpected went wrong" instead of the real message.
 * This reads the blob back out as text and re-parses it as JSON before throwing, so callers get the
 * same {@link ApiError} shape every other failed request in this app produces.
 */
export async function exportEntities(entityType: ImportEntityType): Promise<void> {
  try {
    const response = await apiClient.get(EXPORT_PATH[entityType], { responseType: "blob" });
    const url = URL.createObjectURL(response.data as Blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = EXPORT_FILENAME[entityType];
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
      throw new ApiError(body ?? { message: "Could not export this file." }, error.response.status);
    }
    throw error;
  }
}

/** Multipart upload - axios sets the correct `multipart/form-data` boundary automatically for a FormData body, so no explicit Content-Type is set here. */
export function importEntities(entityType: ImportEntityType, file: File): Promise<ImportJobDto> {
  const formData = new FormData();
  formData.append("file", file);
  return unwrap(apiClient.post(IMPORT_PATH[entityType], formData));
}

export function listImportJobs(params: ListParams = {}): Promise<PageResponse<ImportJobDto>> {
  return unwrap(apiClient.get("/api/v1/import-jobs", { params }));
}

export function getImportJob(jobId: string): Promise<ImportJobDto> {
  return unwrap(apiClient.get(`/api/v1/import-jobs/${jobId}`));
}
