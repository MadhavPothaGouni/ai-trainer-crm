package com.aitrainercrm.platform.course.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.course.dto.CourseDto;
import com.aitrainercrm.platform.course.dto.CreateCourseRequest;
import com.aitrainercrm.platform.course.dto.UpdateCourseRequest;
import com.aitrainercrm.platform.course.entity.Course;
import com.aitrainercrm.platform.course.service.CourseService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** No OWN scope on COURSE (see CourseService's javadoc) - mirrors ProductController exactly. */
@RestController
@RequestMapping("/api/v1/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('COURSE:READ:TEAM','COURSE:READ:DEPARTMENT','COURSE:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<CourseDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<Course> page = courseService.list(principal, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(CourseDto::from).toList()));
    }

    /** Unpaginated active catalog - see CourseService#listActive's javadoc. */
    @GetMapping("/active")
    @PreAuthorize("hasAnyAuthority('COURSE:READ:TEAM','COURSE:READ:DEPARTMENT','COURSE:READ:ORGANIZATION')")
    public ApiResponse<List<CourseDto>> listActive(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(courseService.listActive(principal).stream().map(CourseDto::from).toList());
    }

    @GetMapping("/{courseId}")
    @PreAuthorize("hasAnyAuthority('COURSE:READ:TEAM','COURSE:READ:DEPARTMENT','COURSE:READ:ORGANIZATION')")
    public ApiResponse<CourseDto> get(@PathVariable UUID courseId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(CourseDto.from(courseService.get(principal, courseId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('COURSE:CREATE:TEAM','COURSE:CREATE:DEPARTMENT','COURSE:CREATE:ORGANIZATION')")
    public ApiResponse<CourseDto> create(@Valid @RequestBody CreateCourseRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(CourseDto.from(courseService.create(principal, request)), "Course created");
    }

    @PutMapping("/{courseId}")
    @PreAuthorize("hasAnyAuthority('COURSE:UPDATE:TEAM','COURSE:UPDATE:DEPARTMENT','COURSE:UPDATE:ORGANIZATION')")
    public ApiResponse<CourseDto> update(
            @PathVariable UUID courseId, @Valid @RequestBody UpdateCourseRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(CourseDto.from(courseService.update(principal, courseId, request)), "Course updated");
    }

    @DeleteMapping("/{courseId}")
    @PreAuthorize("hasAnyAuthority('COURSE:DELETE:TEAM','COURSE:DELETE:DEPARTMENT','COURSE:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID courseId, @AuthenticationPrincipal UserPrincipal principal) {
        courseService.delete(principal, courseId);
        return ApiResponse.ok(null, "Course deleted");
    }
}
