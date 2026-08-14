package com.aitrainercrm.platform.trainingsession.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.trainingsession.dto.CreateTrainingSessionExerciseRequest;
import com.aitrainercrm.platform.trainingsession.dto.CreateTrainingSessionRequest;
import com.aitrainercrm.platform.trainingsession.dto.TrainingSessionDto;
import com.aitrainercrm.platform.trainingsession.dto.TrainingSessionExerciseDto;
import com.aitrainercrm.platform.trainingsession.dto.UpdateTrainingSessionExerciseRequest;
import com.aitrainercrm.platform.trainingsession.dto.UpdateTrainingSessionRequest;
import com.aitrainercrm.platform.trainingsession.dto.UpdateTrainingSessionStatusRequest;
import com.aitrainercrm.platform.trainingsession.entity.TrainingSession;
import com.aitrainercrm.platform.trainingsession.service.TrainingSessionService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Mirrors ContractController's shape exactly - see TicketController's own javadoc for the reasoning behind the coarse-@PreAuthorize-then-service-layer-record-check split. */
@RestController
@RequestMapping("/api/v1/training-sessions")
@RequiredArgsConstructor
public class TrainingSessionController {

    private final TrainingSessionService trainingSessionService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('TRAINING_SESSION:READ:OWN','TRAINING_SESSION:READ:TEAM','TRAINING_SESSION:READ:DEPARTMENT','TRAINING_SESSION:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<TrainingSessionDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<TrainingSession> page = trainingSessionService.list(principal, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(TrainingSessionDto::from).toList()));
    }

    @GetMapping("/{trainingSessionId}")
    @PreAuthorize("hasAnyAuthority('TRAINING_SESSION:READ:OWN','TRAINING_SESSION:READ:TEAM','TRAINING_SESSION:READ:DEPARTMENT','TRAINING_SESSION:READ:ORGANIZATION')")
    public ApiResponse<TrainingSessionDto> get(@PathVariable UUID trainingSessionId, @AuthenticationPrincipal UserPrincipal principal) {
        TrainingSession session = trainingSessionService.get(principal, trainingSessionId);
        return ApiResponse.ok(TrainingSessionDto.from(session, trainingSessionService.getExercises(principal, trainingSessionId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('TRAINING_SESSION:CREATE:OWN','TRAINING_SESSION:CREATE:TEAM','TRAINING_SESSION:CREATE:DEPARTMENT','TRAINING_SESSION:CREATE:ORGANIZATION')")
    public ApiResponse<TrainingSessionDto> create(
            @Valid @RequestBody CreateTrainingSessionRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(TrainingSessionDto.from(trainingSessionService.create(principal, request)), "Training session created");
    }

    @PutMapping("/{trainingSessionId}")
    @PreAuthorize("hasAnyAuthority('TRAINING_SESSION:UPDATE:OWN','TRAINING_SESSION:UPDATE:TEAM','TRAINING_SESSION:UPDATE:DEPARTMENT','TRAINING_SESSION:UPDATE:ORGANIZATION')")
    public ApiResponse<TrainingSessionDto> update(
            @PathVariable UUID trainingSessionId,
            @Valid @RequestBody UpdateTrainingSessionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        TrainingSession session = trainingSessionService.update(principal, trainingSessionId, request);
        return ApiResponse.ok(
                TrainingSessionDto.from(session, trainingSessionService.getExercises(principal, trainingSessionId)), "Training session updated");
    }

    @PatchMapping("/{trainingSessionId}/status")
    @PreAuthorize("hasAnyAuthority('TRAINING_SESSION:UPDATE:OWN','TRAINING_SESSION:UPDATE:TEAM','TRAINING_SESSION:UPDATE:DEPARTMENT','TRAINING_SESSION:UPDATE:ORGANIZATION')")
    public ApiResponse<TrainingSessionDto> updateStatus(
            @PathVariable UUID trainingSessionId,
            @Valid @RequestBody UpdateTrainingSessionStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(
                TrainingSessionDto.from(trainingSessionService.updateStatus(principal, trainingSessionId, request.status())), "Status updated");
    }

    @DeleteMapping("/{trainingSessionId}")
    @PreAuthorize("hasAnyAuthority('TRAINING_SESSION:DELETE:OWN','TRAINING_SESSION:DELETE:TEAM','TRAINING_SESSION:DELETE:DEPARTMENT','TRAINING_SESSION:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID trainingSessionId, @AuthenticationPrincipal UserPrincipal principal) {
        trainingSessionService.delete(principal, trainingSessionId);
        return ApiResponse.ok(null, "Training session deleted");
    }

    @PostMapping("/{trainingSessionId}/exercises")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('TRAINING_SESSION:UPDATE:OWN','TRAINING_SESSION:UPDATE:TEAM','TRAINING_SESSION:UPDATE:DEPARTMENT','TRAINING_SESSION:UPDATE:ORGANIZATION')")
    public ApiResponse<TrainingSessionExerciseDto> addExercise(
            @PathVariable UUID trainingSessionId,
            @Valid @RequestBody CreateTrainingSessionExerciseRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(
                TrainingSessionExerciseDto.from(trainingSessionService.addExercise(principal, trainingSessionId, request)), "Exercise added");
    }

    @PutMapping("/{trainingSessionId}/exercises/{exerciseEntryId}")
    @PreAuthorize("hasAnyAuthority('TRAINING_SESSION:UPDATE:OWN','TRAINING_SESSION:UPDATE:TEAM','TRAINING_SESSION:UPDATE:DEPARTMENT','TRAINING_SESSION:UPDATE:ORGANIZATION')")
    public ApiResponse<TrainingSessionExerciseDto> updateExercise(
            @PathVariable UUID trainingSessionId, @PathVariable UUID exerciseEntryId,
            @Valid @RequestBody UpdateTrainingSessionExerciseRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(
                TrainingSessionExerciseDto.from(trainingSessionService.updateExercise(principal, trainingSessionId, exerciseEntryId, request)),
                "Exercise updated");
    }

    @DeleteMapping("/{trainingSessionId}/exercises/{exerciseEntryId}")
    @PreAuthorize("hasAnyAuthority('TRAINING_SESSION:UPDATE:OWN','TRAINING_SESSION:UPDATE:TEAM','TRAINING_SESSION:UPDATE:DEPARTMENT','TRAINING_SESSION:UPDATE:ORGANIZATION')")
    public ApiResponse<Void> removeExercise(
            @PathVariable UUID trainingSessionId, @PathVariable UUID exerciseEntryId, @AuthenticationPrincipal UserPrincipal principal) {
        trainingSessionService.removeExercise(principal, trainingSessionId, exerciseEntryId);
        return ApiResponse.ok(null, "Exercise removed");
    }
}
