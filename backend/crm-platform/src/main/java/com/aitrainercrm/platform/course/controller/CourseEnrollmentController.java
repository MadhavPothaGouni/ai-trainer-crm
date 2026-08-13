package com.aitrainercrm.platform.course.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.course.dto.CourseEnrollmentDto;
import com.aitrainercrm.platform.course.dto.CreateCourseEnrollmentRequest;
import com.aitrainercrm.platform.course.dto.UpdateCourseEnrollmentProgressRequest;
import com.aitrainercrm.platform.course.entity.CourseEnrollment;
import com.aitrainercrm.platform.course.service.CourseEnrollmentService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Mirrors TicketController's shape exactly - see CourseEnrollmentService's javadoc for the OWN/TEAM/DEPARTMENT/ORGANIZATION reasoning. */
@RestController
@RequestMapping("/api/v1/course-enrollments")
@RequiredArgsConstructor
public class CourseEnrollmentController {

    private final CourseEnrollmentService courseEnrollmentService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('COURSE_ENROLLMENT:READ:OWN','COURSE_ENROLLMENT:READ:TEAM','COURSE_ENROLLMENT:READ:DEPARTMENT','COURSE_ENROLLMENT:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<CourseEnrollmentDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<CourseEnrollment> page = courseEnrollmentService.list(principal, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(CourseEnrollmentDto::from).toList()));
    }

    @GetMapping("/{enrollmentId}")
    @PreAuthorize("hasAnyAuthority('COURSE_ENROLLMENT:READ:OWN','COURSE_ENROLLMENT:READ:TEAM','COURSE_ENROLLMENT:READ:DEPARTMENT','COURSE_ENROLLMENT:READ:ORGANIZATION')")
    public ApiResponse<CourseEnrollmentDto> get(@PathVariable UUID enrollmentId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(CourseEnrollmentDto.from(courseEnrollmentService.get(principal, enrollmentId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('COURSE_ENROLLMENT:CREATE:OWN','COURSE_ENROLLMENT:CREATE:TEAM','COURSE_ENROLLMENT:CREATE:DEPARTMENT','COURSE_ENROLLMENT:CREATE:ORGANIZATION')")
    public ApiResponse<CourseEnrollmentDto> create(
            @Valid @RequestBody CreateCourseEnrollmentRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(CourseEnrollmentDto.from(courseEnrollmentService.create(principal, request)), "Enrolled");
    }

    @PatchMapping("/{enrollmentId}/progress")
    @PreAuthorize("hasAnyAuthority('COURSE_ENROLLMENT:UPDATE:OWN','COURSE_ENROLLMENT:UPDATE:TEAM','COURSE_ENROLLMENT:UPDATE:DEPARTMENT','COURSE_ENROLLMENT:UPDATE:ORGANIZATION')")
    public ApiResponse<CourseEnrollmentDto> updateProgress(
            @PathVariable UUID enrollmentId, @Valid @RequestBody UpdateCourseEnrollmentProgressRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        CourseEnrollment updated = courseEnrollmentService.updateProgress(principal, enrollmentId, request.status(), request.scorePercent());
        return ApiResponse.ok(CourseEnrollmentDto.from(updated), "Progress updated");
    }

    @DeleteMapping("/{enrollmentId}")
    @PreAuthorize("hasAnyAuthority('COURSE_ENROLLMENT:DELETE:OWN','COURSE_ENROLLMENT:DELETE:TEAM','COURSE_ENROLLMENT:DELETE:DEPARTMENT','COURSE_ENROLLMENT:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID enrollmentId, @AuthenticationPrincipal UserPrincipal principal) {
        courseEnrollmentService.delete(principal, enrollmentId);
        return ApiResponse.ok(null, "Enrollment removed");
    }
}
