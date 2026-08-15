package com.aitrainercrm.platform.nutritionlog.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.nutritionlog.dto.CreateNutritionLogRequest;
import com.aitrainercrm.platform.nutritionlog.dto.NutritionLogDto;
import com.aitrainercrm.platform.nutritionlog.dto.UpdateNutritionLogRequest;
import com.aitrainercrm.platform.nutritionlog.entity.NutritionLog;
import com.aitrainercrm.platform.nutritionlog.service.NutritionLogService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Standard CRUD, no status endpoint - a logged meal is a point-in-time fact, mirrors ProgressPhotoController's shape. */
@RestController
@RequestMapping("/api/v1/nutrition-logs")
@RequiredArgsConstructor
public class NutritionLogController {

    private final NutritionLogService nutritionLogService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('NUTRITION_LOG:READ:OWN','NUTRITION_LOG:READ:TEAM','NUTRITION_LOG:READ:DEPARTMENT','NUTRITION_LOG:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<NutritionLogDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<NutritionLog> page = nutritionLogService.list(principal, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(NutritionLogDto::from).toList()));
    }

    @GetMapping("/{nutritionLogId}")
    @PreAuthorize("hasAnyAuthority('NUTRITION_LOG:READ:OWN','NUTRITION_LOG:READ:TEAM','NUTRITION_LOG:READ:DEPARTMENT','NUTRITION_LOG:READ:ORGANIZATION')")
    public ApiResponse<NutritionLogDto> get(@PathVariable UUID nutritionLogId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(NutritionLogDto.from(nutritionLogService.get(principal, nutritionLogId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('NUTRITION_LOG:CREATE:OWN','NUTRITION_LOG:CREATE:TEAM','NUTRITION_LOG:CREATE:DEPARTMENT','NUTRITION_LOG:CREATE:ORGANIZATION')")
    public ApiResponse<NutritionLogDto> create(
            @Valid @RequestBody CreateNutritionLogRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(NutritionLogDto.from(nutritionLogService.create(principal, request)), "Meal logged");
    }

    @PutMapping("/{nutritionLogId}")
    @PreAuthorize("hasAnyAuthority('NUTRITION_LOG:UPDATE:OWN','NUTRITION_LOG:UPDATE:TEAM','NUTRITION_LOG:UPDATE:DEPARTMENT','NUTRITION_LOG:UPDATE:ORGANIZATION')")
    public ApiResponse<NutritionLogDto> update(
            @PathVariable UUID nutritionLogId,
            @Valid @RequestBody UpdateNutritionLogRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(NutritionLogDto.from(nutritionLogService.update(principal, nutritionLogId, request)), "Nutrition log updated");
    }

    @DeleteMapping("/{nutritionLogId}")
    @PreAuthorize("hasAnyAuthority('NUTRITION_LOG:DELETE:OWN','NUTRITION_LOG:DELETE:TEAM','NUTRITION_LOG:DELETE:DEPARTMENT','NUTRITION_LOG:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID nutritionLogId, @AuthenticationPrincipal UserPrincipal principal) {
        nutritionLogService.delete(principal, nutritionLogId);
        return ApiResponse.ok(null, "Nutrition log deleted");
    }
}
