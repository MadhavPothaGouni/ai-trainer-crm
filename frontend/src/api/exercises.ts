import { apiClient, unwrap } from "../lib/apiClient";
import type { CreateExerciseRequest, ExerciseDto, PageResponse, UpdateExerciseRequest } from "../types/api";

export interface ListExercisesParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listExercises(params: ListExercisesParams = {}): Promise<PageResponse<ExerciseDto>> {
  return unwrap(apiClient.get("/api/v1/exercises", { params }));
}

/** The unpaginated active catalog - see ExerciseService#listActive's javadoc, same shape as listActiveCourses. */
export function listActiveExercises(): Promise<ExerciseDto[]> {
  return unwrap(apiClient.get("/api/v1/exercises/active"));
}

export function getExercise(exerciseId: string): Promise<ExerciseDto> {
  return unwrap(apiClient.get(`/api/v1/exercises/${exerciseId}`));
}

export function createExercise(request: CreateExerciseRequest): Promise<ExerciseDto> {
  return unwrap(apiClient.post("/api/v1/exercises", request));
}

export function updateExercise(exerciseId: string, request: UpdateExerciseRequest): Promise<ExerciseDto> {
  return unwrap(apiClient.put(`/api/v1/exercises/${exerciseId}`, request));
}

export function deleteExercise(exerciseId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/exercises/${exerciseId}`));
}
