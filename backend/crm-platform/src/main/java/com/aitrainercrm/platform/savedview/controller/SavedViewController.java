package com.aitrainercrm.platform.savedview.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.savedview.dto.CreateSavedViewRequest;
import com.aitrainercrm.platform.savedview.dto.SavedViewDto;
import com.aitrainercrm.platform.savedview.dto.UpdateSavedViewRequest;
import com.aitrainercrm.platform.savedview.entity.SavedView;
import com.aitrainercrm.platform.savedview.service.SavedViewService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * No {@code @PreAuthorize} anywhere - every method is scoped to the caller's own id inside {@code
 * SavedViewService}, same shape {@code NotificationController} uses. {@code entityType} is a
 * required query param on {@link #list} (never "show me every saved view across every entity
 * type"), so each list-page's own request maps onto exactly the views relevant to it.
 */
@RestController
@RequestMapping("/api/v1/saved-views")
@RequiredArgsConstructor
public class SavedViewController {

    private final SavedViewService savedViewService;

    @GetMapping
    public ApiResponse<List<SavedViewDto>> list(
            @RequestParam SavedView.EntityType entityType, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(savedViewService.list(principal, entityType));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SavedViewDto> create(
            @Valid @RequestBody CreateSavedViewRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(savedViewService.create(principal, request), "Saved view created");
    }

    @PutMapping("/{viewId}")
    public ApiResponse<SavedViewDto> update(
            @PathVariable UUID viewId, @Valid @RequestBody UpdateSavedViewRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(savedViewService.update(principal, viewId, request), "Saved view updated");
    }

    @PatchMapping("/{viewId}/default")
    public ApiResponse<SavedViewDto> setDefault(@PathVariable UUID viewId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(savedViewService.setDefault(principal, viewId), "Default view updated");
    }

    @DeleteMapping("/{viewId}")
    public ApiResponse<Void> delete(@PathVariable UUID viewId, @AuthenticationPrincipal UserPrincipal principal) {
        savedViewService.delete(principal, viewId);
        return ApiResponse.ok(null, "Saved view deleted");
    }
}
