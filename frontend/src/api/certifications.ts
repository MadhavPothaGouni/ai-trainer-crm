import { apiClient, unwrap } from "../lib/apiClient";
import type {
  AwardCertificationRequest,
  CertificationDto,
  CreateCertificationRequest,
  PageResponse,
  UpdateCertificationRequest,
  UpdateUserCertificationStatusRequest,
  UserCertificationDto,
} from "../types/api";

export interface ListCertificationsParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listCertifications(params: ListCertificationsParams = {}): Promise<PageResponse<CertificationDto>> {
  return unwrap(apiClient.get("/api/v1/certifications", { params }));
}

export function listActiveCertifications(): Promise<CertificationDto[]> {
  return unwrap(apiClient.get("/api/v1/certifications/active"));
}

export function getCertification(certificationId: string): Promise<CertificationDto> {
  return unwrap(apiClient.get(`/api/v1/certifications/${certificationId}`));
}

export function createCertification(request: CreateCertificationRequest): Promise<CertificationDto> {
  return unwrap(apiClient.post("/api/v1/certifications", request));
}

export function updateCertification(certificationId: string, request: UpdateCertificationRequest): Promise<CertificationDto> {
  return unwrap(apiClient.put(`/api/v1/certifications/${certificationId}`, request));
}

export function deleteCertification(certificationId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/certifications/${certificationId}`));
}

export interface ListUserCertificationsParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listUserCertifications(params: ListUserCertificationsParams = {}): Promise<PageResponse<UserCertificationDto>> {
  return unwrap(apiClient.get("/api/v1/user-certifications", { params }));
}

export function getUserCertification(userCertificationId: string): Promise<UserCertificationDto> {
  return unwrap(apiClient.get(`/api/v1/user-certifications/${userCertificationId}`));
}

export function awardCertification(request: AwardCertificationRequest): Promise<UserCertificationDto> {
  return unwrap(apiClient.post("/api/v1/user-certifications", request));
}

export function updateUserCertificationStatus(
  userCertificationId: string,
  request: UpdateUserCertificationStatusRequest,
): Promise<UserCertificationDto> {
  return unwrap(apiClient.patch(`/api/v1/user-certifications/${userCertificationId}/status`, request));
}

export function deleteUserCertification(userCertificationId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/user-certifications/${userCertificationId}`));
}
