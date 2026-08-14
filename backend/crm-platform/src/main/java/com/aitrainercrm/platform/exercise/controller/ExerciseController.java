package com.aitrainercrm.platform.exercise.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.exercise.dto.CreateExerciseRequest;
import com.aitrainercrm.platform.exercise.dto.ExerciseDto;
import com.aitrainercrm.platform.exercise.dto.UpdateExerciseRequest;
import com.aitrainercrm.platform.exercise.entity.Exercise;
import com.aitrainercrm.platform.exercise.service.ExerciseService;
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

/** No OWN scope on EXERCISE (see ExerciseService's javadoc) - mirrors CourseController exactly. */
@RestController
@RequestMapping("/api/v1/exercises")
@RequiredArgsConstructor
public class ExerciseController {

    private final ExerciseService exerciseService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('EXERCISE:READ:TEAM','EXERCISE:READ:DEPARTMENT','EXERCISE:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<ExerciseDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<Exercise> page = exerciseService.list(principal, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(ExerciseDto::from).toList()));
    }

    /** Unpaginated active catalog - see ExerciseService#listActive's javadoc. */
    @GetMapping("/active")
    @PreAuthorize("hasAnyAuthority('EXERCISE:READ:TEAM','EXERCISE:READ:DEPARTMENT','EXERCISE:READ:ORGANIZATION')")
    public ApiResponse<List<ExerciseDto>> listActive(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(exerciseService.listActive(principal).stream().map(ExerciseDto::from).toList());
    }

    @GetMapping("/{exerciseId}")
    @PreAuthorize("hasAnyAuthority('EXERCISE:READ:TEAM','EXERCISE:READ:DEPARTMENT','EXERCISE:READ:ORGANIZATION')")
    public ApiResponse<ExerciseDto> get(@PathVariable UUID exerciseId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(ExerciseDto.from(exerciseService.get(principal, exerciseId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('EXERCISE:CREATE:TEAM','EXERCISE:CREATE:DEPARTMENT','EXERCISE:CREATE:ORGANIZATION')")
    public ApiResponse<ExerciseDto> create(@Valid @RequestBody CreateExerciseRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(ExerciseDto.from(exerciseService.create(principal, request)), "Exercise created");
    }

    @PutMapping("/{exerciseId}")
    @PreAuthorize("hasAnyAuthority('EXERCISE:UPDATE:TEAM','EXERCISE:UPDATE:DEPARTMENT','EXERCISE:UPDATE:ORGANIZATION')")
    public ApiResponse<ExerciseDto> update(
            @PathVariable UUID exerciseId, @Valid @RequestBody UpdateExerciseRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(ExerciseDto.from(exerciseService.update(principal, exerciseId, request)), "Exercise updated");
    }

    @DeleteMapping("/{exerciseId}")
    @PreAuthorize("hasAnyAuthority('EXERCISE:DELETE:TEAM','EXERCISE:DELETE:DEPARTMENT','EXERCISE:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID exerciseId, @AuthenticationPrincipal UserPrincipal principal) {
        exerciseService.delete(principal, exerciseId);
        return ApiResponse.ok(null, "Exercise deleted");
    }
}
