package com.aitrainercrm.platform.salesgoals.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.salesgoals.dto.CreateSalesGoalRequest;
import com.aitrainercrm.platform.salesgoals.dto.SalesGoalDto;
import com.aitrainercrm.platform.salesgoals.dto.UpdateSalesGoalRequest;
import com.aitrainercrm.platform.salesgoals.service.SalesGoalService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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

/**
 * Admin config CRUD, entirely gated by a single ORGANIZATION-scope authority per action - same
 * shape as TerritoryRuleController/LeadScoringRuleController. {@link #mine} is the one exception:
 * no {@code @PreAuthorize} at all, the same self-scoped shape {@code NotificationController} uses
 * for a teammate's own inbox - see {@code SalesGoalService}'s javadoc.
 */
@RestController
@RequestMapping("/api/v1/sales-goals")
@RequiredArgsConstructor
public class SalesGoalController {

    private final SalesGoalService salesGoalService;

    @GetMapping
    @PreAuthorize("hasAuthority('SALES_GOAL:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<SalesGoalDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(PageResponse.from(salesGoalService.list(principal, pageable)));
    }

    @GetMapping("/mine")
    public ApiResponse<List<SalesGoalDto>> mine(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(salesGoalService.myGoals(principal));
    }

    @GetMapping("/{goalId}")
    @PreAuthorize("hasAuthority('SALES_GOAL:READ:ORGANIZATION')")
    public ApiResponse<SalesGoalDto> get(@PathVariable UUID goalId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(salesGoalService.get(principal, goalId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SALES_GOAL:CREATE:ORGANIZATION')")
    public ApiResponse<SalesGoalDto> create(
            @Valid @RequestBody CreateSalesGoalRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(salesGoalService.create(principal, request), "Sales goal created");
    }

    @PutMapping("/{goalId}")
    @PreAuthorize("hasAuthority('SALES_GOAL:UPDATE:ORGANIZATION')")
    public ApiResponse<SalesGoalDto> update(
            @PathVariable UUID goalId, @Valid @RequestBody UpdateSalesGoalRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(salesGoalService.update(principal, goalId, request), "Sales goal updated");
    }

    @DeleteMapping("/{goalId}")
    @PreAuthorize("hasAuthority('SALES_GOAL:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID goalId, @AuthenticationPrincipal UserPrincipal principal) {
        salesGoalService.delete(principal, goalId);
        return ApiResponse.ok(null, "Sales goal deleted");
    }
}
