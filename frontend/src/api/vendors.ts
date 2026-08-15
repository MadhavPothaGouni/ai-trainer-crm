import { apiClient, unwrap } from "../lib/apiClient";
import type { CreateVendorRequest, PageResponse, UpdateVendorRequest, VendorDto } from "../types/api";

export interface ListVendorsParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listVendors(params: ListVendorsParams = {}): Promise<PageResponse<VendorDto>> {
  return unwrap(apiClient.get("/api/v1/vendors", { params }));
}

export function getVendor(vendorId: string): Promise<VendorDto> {
  return unwrap(apiClient.get(`/api/v1/vendors/${vendorId}`));
}

export function createVendor(request: CreateVendorRequest): Promise<VendorDto> {
  return unwrap(apiClient.post("/api/v1/vendors", request));
}

export function updateVendor(vendorId: string, request: UpdateVendorRequest): Promise<VendorDto> {
  return unwrap(apiClient.put(`/api/v1/vendors/${vendorId}`, request));
}

export function deleteVendor(vendorId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/vendors/${vendorId}`));
}
