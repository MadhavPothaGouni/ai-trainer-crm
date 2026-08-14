package com.aitrainercrm.platform.nutritionplan.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.nutritionplan.dto.CreateNutritionPlanRequest;
import com.aitrainercrm.platform.nutritionplan.dto.NutritionPlanDto;
import com.aitrainercrm.platform.nutritionplan.dto.UpdateNutritionPlanRequest;
import com.aitrainercrm.platform.nutritionplan.dto.UpdateNutritionPlanStatusRequest;
import com.aitrainercrm.platform.nutritionplan.entity.NutritionPlan;
import com.aitrainercrm.platform.nutritionplan.service.NutritionPlanService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Mirrors ClientGoalController's shape exactly - see TicketController's own javadoc for the reasoning behind the coarse-@PreAuthorize-then-service-layer-record-check split. */
@RestController
@RequestMapping("/api/v1/nutrition-plans")
@RequiredArgsConstructor
public class NutritionPlanController {

    private final NutritionPlanService nutritionPlanService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('NUTRITION_PLAN:READ:OWN','NUTRITION_PLAN:READ:TEAM','NUTRITION_PLAN:READ:DEPARTMENT','NUTRITION_PLAN:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<NutritionPlanDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<NutritionPlan> page = nutritionPlanService.list(principal, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(NutritionPlanDto::from).toList()));
    }

    @GetMapping("/{nutritionPlanId}")
    @PreAuthorize("hasAnyAuthority('NUTRITION_PLAN:READ:OWN','NUTRITION_PLAN:READ:TEAM','NUTRITION_PLAN:READ:DEPARTMENT','NUTRITION_PLAN:READ:ORGANIZATION')")
    public ApiResponse<NutritionPlanDto> get(@PathVariable UUID nutritionPlanId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(NutritionPlanDto.from(nutritionPlanService.get(principal, nutritionPlanId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('NUTRITION_PLAN:CREATE:OWN','NUTRITION_PLAN:CREATE:TEAM','NUTRITION_PLAN:CREATE:DEPARTMENT','NUTRITION_PLAN:CREATE:ORGANIZATION')")
    public ApiResponse<NutritionPlanDto> create(
            @Valid @RequestBody CreateNutritionPlanRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(NutritionPlanDto.from(nutritionPlanService.create(principal, request)), "Nutrition plan created");
    }

    @PutMapping("/{nutritionPlanId}")
    @PreAuthorize("hasAnyAuthority('NUTRITION_PLAN:UPDATE:OWN','NUTRITION_PLAN:UPDATE:TEAM','NUTRITION_PLAN:UPDATE:DEPARTMENT','NUTRITION_PLAN:UPDATE:ORGANIZATION')")
    public ApiResponse<NutritionPlanDto> update(
            @PathVariable UUID nutritionPlanId, @Valid @RequestBody UpdateNutritionPlanRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(NutritionPlanDto.from(nutritionPlanService.update(principal, nutritionPlanId, request)), "Nutrition plan updated");
    }

    @PatchMapping("/{nutritionPlanId}/status")
    @PreAuthorize("hasAnyAuthority('NUTRITION_PLAN:UPDATE:OWN','NUTRITION_PLAN:UPDATE:TEAM','NUTRITION_PLAN:UPDATE:DEPARTMENT','NUTRITION_PLAN:UPDATE:ORGANIZATION')")
    public ApiResponse<NutritionPlanDto> updateStatus(
            @PathVariable UUID nutritionPlanId, @Valid @RequestBody UpdateNutritionPlanStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(NutritionPlanDto.from(nutritionPlanService.updateStatus(principal, nutritionPlanId, request.status())), "Status updated");
    }

    @DeleteMapping("/{nutritionPlanId}")
    @PreAuthorize("hasAnyAuthority('NUTRITION_PLAN:DELETE:OWN','NUTRITION_PLAN:DELETE:TEAM','NUTRITION_PLAN:DELETE:DEPARTMENT','NUTRITION_PLAN:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID nutritionPlanId, @AuthenticationPrincipal UserPrincipal principal) {
        nutritionPlanService.delete(principal, nutritionPlanId);
        return ApiResponse.ok(null, "Nutrition plan deleted");
    }
}
