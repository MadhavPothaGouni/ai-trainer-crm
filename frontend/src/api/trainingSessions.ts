import { apiClient, unwrap } from "../lib/apiClient";
import type {
  CreateTrainingSessionRequest,
  PageResponse,
  TrainingSessionDto,
  UpdateTrainingSessionRequest,
  UpdateTrainingSessionStatusRequest,
} from "../types/api";

export interface ListTrainingSessionsParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listTrainingSessions(params: ListTrainingSessionsParams = {}): Promise<PageResponse<TrainingSessionDto>> {
  return unwrap(apiClient.get("/api/v1/training-sessions", { params }));
}

export function getTrainingSession(trainingSessionId: string): Promise<TrainingSessionDto> {
  return unwrap(apiClient.get(`/api/v1/training-sessions/${trainingSessionId}`));
}

export function createTrainingSession(request: CreateTrainingSessionRequest): Promise<TrainingSessionDto> {
  return unwrap(apiClient.post("/api/v1/training-sessions", request));
}

export function updateTrainingSession(trainingSessionId: string, request: UpdateTrainingSessionRequest): Promise<TrainingSessionDto> {
  return unwrap(apiClient.put(`/api/v1/training-sessions/${trainingSessionId}`, request));
}

export function updateTrainingSessionStatus(
  trainingSessionId: string,
  request: UpdateTrainingSessionStatusRequest,
): Promise<TrainingSessionDto> {
  return unwrap(apiClient.patch(`/api/v1/training-sessions/${trainingSessionId}/status`, request));
}

export function deleteTrainingSession(trainingSessionId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/training-sessions/${trainingSessionId}`));
}
