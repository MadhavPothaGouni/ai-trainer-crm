import { apiClient, unwrap } from "../lib/apiClient";
import type {
  ContractDto,
  CreateContractRequest,
  PageResponse,
  UpdateContractRequest,
  UpdateContractStatusRequest,
} from "../types/api";

export interface ListContractsParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listContracts(params: ListContractsParams = {}): Promise<PageResponse<ContractDto>> {
  return unwrap(apiClient.get("/api/v1/contracts", { params }));
}

export function getContract(contractId: string): Promise<ContractDto> {
  return unwrap(apiClient.get(`/api/v1/contracts/${contractId}`));
}

export function createContract(request: CreateContractRequest): Promise<ContractDto> {
  return unwrap(apiClient.post("/api/v1/contracts", request));
}

export function updateContract(contractId: string, request: UpdateContractRequest): Promise<ContractDto> {
  return unwrap(apiClient.put(`/api/v1/contracts/${contractId}`, request));
}

export function updateContractStatus(contractId: string, request: UpdateContractStatusRequest): Promise<ContractDto> {
  return unwrap(apiClient.patch(`/api/v1/contracts/${contractId}/status`, request));
}

export function deleteContract(contractId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/contracts/${contractId}`));
}
