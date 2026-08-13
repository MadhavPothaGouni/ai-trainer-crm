package com.aitrainercrm.platform.course.dto;

import com.aitrainercrm.platform.course.entity.CourseEnrollment;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Drives {@code CourseEnrollmentService#updateProgress} - see its javadoc for the exact status/score/timestamp transitions this triggers depending on the requested status. */
public record UpdateCourseEnrollmentProgressRequest(@NotNull CourseEnrollment.Status status, @Min(0) @Max(100) Integer scorePercent) {
}
