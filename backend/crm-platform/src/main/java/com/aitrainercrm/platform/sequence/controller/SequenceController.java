package com.aitrainercrm.platform.sequence.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.sequence.dto.CreateSequenceRequest;
import com.aitrainercrm.platform.sequence.dto.CreateSequenceStepRequest;
import com.aitrainercrm.platform.sequence.dto.SequenceDto;
import com.aitrainercrm.platform.sequence.dto.SequenceStepDto;
import com.aitrainercrm.platform.sequence.dto.UpdateSequenceRequest;
import com.aitrainercrm.platform.sequence.dto.UpdateSequenceStepRequest;
import com.aitrainercrm.platform.sequence.entity.Sequence;
import com.aitrainercrm.platform.sequence.service.SequenceService;
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

/** No OWN scope on SEQUENCE (see SequenceService's javadoc) - mirrors CourseController exactly. Steps are sub-resources, mirroring QuoteController's line-item endpoints. */
@RestController
@RequestMapping("/api/v1/sequences")
@RequiredArgsConstructor
public class SequenceController {

    private final SequenceService sequenceService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('SEQUENCE:READ:TEAM','SEQUENCE:READ:DEPARTMENT','SEQUENCE:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<SequenceDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<Sequence> page = sequenceService.list(principal, pageable);
        return ApiResponse.ok(PageResponse.from(
                page, page.getContent().stream().map(sequence -> SequenceDto.from(sequence, sequenceService.getSteps(principal, sequence.getId()))).toList()));
    }

    /** Unpaginated active catalog - see CourseService#listActive's javadoc for the same pattern. */
    @GetMapping("/active")
    @PreAuthorize("hasAnyAuthority('SEQUENCE:READ:TEAM','SEQUENCE:READ:DEPARTMENT','SEQUENCE:READ:ORGANIZATION')")
    public ApiResponse<List<SequenceDto>> listActive(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(sequenceService.listActive(principal).stream()
                .map(sequence -> SequenceDto.from(sequence, sequenceService.getSteps(principal, sequence.getId())))
                .toList());
    }

    @GetMapping("/{sequenceId}")
    @PreAuthorize("hasAnyAuthority('SEQUENCE:READ:TEAM','SEQUENCE:READ:DEPARTMENT','SEQUENCE:READ:ORGANIZATION')")
    public ApiResponse<SequenceDto> get(@PathVariable UUID sequenceId, @AuthenticationPrincipal UserPrincipal principal) {
        Sequence sequence = sequenceService.get(principal, sequenceId);
        return ApiResponse.ok(SequenceDto.from(sequence, sequenceService.getSteps(principal, sequenceId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('SEQUENCE:CREATE:TEAM','SEQUENCE:CREATE:DEPARTMENT','SEQUENCE:CREATE:ORGANIZATION')")
    public ApiResponse<SequenceDto> create(@Valid @RequestBody CreateSequenceRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        Sequence sequence = sequenceService.create(principal, request);
        return ApiResponse.ok(SequenceDto.from(sequence, List.of()), "Sequence created");
    }

    @PutMapping("/{sequenceId}")
    @PreAuthorize("hasAnyAuthority('SEQUENCE:UPDATE:TEAM','SEQUENCE:UPDATE:DEPARTMENT','SEQUENCE:UPDATE:ORGANIZATION')")
    public ApiResponse<SequenceDto> update(
            @PathVariable UUID sequenceId, @Valid @RequestBody UpdateSequenceRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        Sequence sequence = sequenceService.update(principal, sequenceId, request);
        return ApiResponse.ok(SequenceDto.from(sequence, sequenceService.getSteps(principal, sequenceId)), "Sequence updated");
    }

    @DeleteMapping("/{sequenceId}")
    @PreAuthorize("hasAnyAuthority('SEQUENCE:DELETE:TEAM','SEQUENCE:DELETE:DEPARTMENT','SEQUENCE:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID sequenceId, @AuthenticationPrincipal UserPrincipal principal) {
        sequenceService.delete(principal, sequenceId);
        return ApiResponse.ok(null, "Sequence deleted");
    }

    @PostMapping("/{sequenceId}/steps")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('SEQUENCE:UPDATE:TEAM','SEQUENCE:UPDATE:DEPARTMENT','SEQUENCE:UPDATE:ORGANIZATION')")
    public ApiResponse<SequenceStepDto> addStep(
            @PathVariable UUID sequenceId, @Valid @RequestBody CreateSequenceStepRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(SequenceStepDto.from(sequenceService.addStep(principal, sequenceId, request)), "Step added");
    }

    @PutMapping("/{sequenceId}/steps/{stepId}")
    @PreAuthorize("hasAnyAuthority('SEQUENCE:UPDATE:TEAM','SEQUENCE:UPDATE:DEPARTMENT','SEQUENCE:UPDATE:ORGANIZATION')")
    public ApiResponse<SequenceStepDto> updateStep(
            @PathVariable UUID sequenceId, @PathVariable UUID stepId,
            @Valid @RequestBody UpdateSequenceStepRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(SequenceStepDto.from(sequenceService.updateStep(principal, sequenceId, stepId, request)), "Step updated");
    }

    @DeleteMapping("/{sequenceId}/steps/{stepId}")
    @PreAuthorize("hasAnyAuthority('SEQUENCE:UPDATE:TEAM','SEQUENCE:UPDATE:DEPARTMENT','SEQUENCE:UPDATE:ORGANIZATION')")
    public ApiResponse<Void> removeStep(
            @PathVariable UUID sequenceId, @PathVariable UUID stepId, @AuthenticationPrincipal UserPrincipal principal) {
        sequenceService.removeStep(principal, sequenceId, stepId);
        return ApiResponse.ok(null, "Step removed");
    }
}
