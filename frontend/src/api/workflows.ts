import { apiClient, unwrap } from "../lib/apiClient";
import type {
  CreateWorkflowRequest,
  PageResponse,
  RunWorkflowRequest,
  SetWorkflowActiveRequest,
  UpdateWorkflowRequest,
  WorkflowDto,
  WorkflowRunDto,
} from "../types/api";

export interface ListParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listWorkflows(params: ListParams = {}): Promise<PageResponse<WorkflowDto>> {
  return unwrap(apiClient.get("/api/v1/workflows", { params }));
}

export function getWorkflow(workflowId: string): Promise<WorkflowDto> {
  return unwrap(apiClient.get(`/api/v1/workflows/${workflowId}`));
}

export function createWorkflow(request: CreateWorkflowRequest): Promise<WorkflowDto> {
  return unwrap(apiClient.post("/api/v1/workflows", request));
}

export function updateWorkflow(workflowId: string, request: UpdateWorkflowRequest): Promise<WorkflowDto> {
  return unwrap(apiClient.put(`/api/v1/workflows/${workflowId}`, request));
}

export function setWorkflowActive(workflowId: string, request: SetWorkflowActiveRequest): Promise<WorkflowDto> {
  return unwrap(apiClient.patch(`/api/v1/workflows/${workflowId}/active`, request));
}

export function runWorkflow(workflowId: string, request: RunWorkflowRequest): Promise<WorkflowDto> {
  return unwrap(apiClient.post(`/api/v1/workflows/${workflowId}/run`, request));
}

export function deleteWorkflow(workflowId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/workflows/${workflowId}`));
}

export function listWorkflowRuns(workflowId: string, params: ListParams = {}): Promise<PageResponse<WorkflowRunDto>> {
  return unwrap(apiClient.get(`/api/v1/workflows/${workflowId}/runs`, { params }));
}
