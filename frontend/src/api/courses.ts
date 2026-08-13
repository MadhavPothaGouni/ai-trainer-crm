import { apiClient, unwrap } from "../lib/apiClient";
import type {
  CourseDto,
  CourseEnrollmentDto,
  CreateCourseEnrollmentRequest,
  CreateCourseRequest,
  PageResponse,
  UpdateCourseEnrollmentProgressRequest,
  UpdateCourseRequest,
} from "../types/api";

export interface ListCoursesParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listCourses(params: ListCoursesParams = {}): Promise<PageResponse<CourseDto>> {
  return unwrap(apiClient.get("/api/v1/courses", { params }));
}

/** The unpaginated active catalog - used by enrollment forms and anywhere the frontend needs "every course to choose from" in one call. See CourseService#listActive's javadoc. */
export function listActiveCourses(): Promise<CourseDto[]> {
  return unwrap(apiClient.get("/api/v1/courses/active"));
}

export function getCourse(courseId: string): Promise<CourseDto> {
  return unwrap(apiClient.get(`/api/v1/courses/${courseId}`));
}

export function createCourse(request: CreateCourseRequest): Promise<CourseDto> {
  return unwrap(apiClient.post("/api/v1/courses", request));
}

export function updateCourse(courseId: string, request: UpdateCourseRequest): Promise<CourseDto> {
  return unwrap(apiClient.put(`/api/v1/courses/${courseId}`, request));
}

export function deleteCourse(courseId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/courses/${courseId}`));
}

export interface ListCourseEnrollmentsParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listCourseEnrollments(params: ListCourseEnrollmentsParams = {}): Promise<PageResponse<CourseEnrollmentDto>> {
  return unwrap(apiClient.get("/api/v1/course-enrollments", { params }));
}

export function getCourseEnrollment(enrollmentId: string): Promise<CourseEnrollmentDto> {
  return unwrap(apiClient.get(`/api/v1/course-enrollments/${enrollmentId}`));
}

export function createCourseEnrollment(request: CreateCourseEnrollmentRequest): Promise<CourseEnrollmentDto> {
  return unwrap(apiClient.post("/api/v1/course-enrollments", request));
}

export function updateCourseEnrollmentProgress(
  enrollmentId: string,
  request: UpdateCourseEnrollmentProgressRequest,
): Promise<CourseEnrollmentDto> {
  return unwrap(apiClient.patch(`/api/v1/course-enrollments/${enrollmentId}/progress`, request));
}

export function deleteCourseEnrollment(enrollmentId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/course-enrollments/${enrollmentId}`));
}
