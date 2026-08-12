package com.aitrainercrm.platform.dedupe.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.dedupe.dto.DuplicateMatchDto;
import com.aitrainercrm.platform.dedupe.dto.MergeDuplicateRequest;
import com.aitrainercrm.platform.dedupe.entity.DuplicateMatch;
import com.aitrainercrm.platform.dedupe.service.DuplicateMatchService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * No {@code @PreAuthorize} anywhere in this controller - every method reuses LEAD/CONTACT/
 * ACCOUNT's own permission, checked inline in {@code DuplicateMatchService} against the specific
 * record(s) each match pairs. {@code entityType} is a required query/path concept throughout
 * (never "show me everything across all three types") precisely so each request maps onto exactly
 * one resource to check - the same reason {@code TicketSlaController} never needed a
 * ticket-type-agnostic bulk endpoint either.
 */
@RestController
@RequestMapping("/api/v1/duplicates")
@RequiredArgsConstructor
public class DuplicateMatchController {

    private final DuplicateMatchService duplicateMatchService;

    @GetMapping
    public ApiResponse<List<DuplicateMatchDto>> list(
            @RequestParam DuplicateMatch.EntityType entityType,
            @RequestParam(defaultValue = "PENDING") DuplicateMatch.Status status,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(duplicateMatchService.list(principal, entityType, status));
    }

    @GetMapping("/{matchId}")
    public ApiResponse<DuplicateMatchDto> get(@PathVariable UUID matchId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(duplicateMatchService.get(principal, matchId));
    }

    @PostMapping("/{matchId}/merge")
    public ApiResponse<DuplicateMatchDto> merge(
            @PathVariable UUID matchId, @Valid @RequestBody MergeDuplicateRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(duplicateMatchService.merge(principal, matchId, request.survivorId()), "Records merged");
    }

    @PostMapping("/{matchId}/dismiss")
    public ApiResponse<DuplicateMatchDto> dismiss(@PathVariable UUID matchId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(duplicateMatchService.dismiss(principal, matchId), "Match dismissed");
    }
}
