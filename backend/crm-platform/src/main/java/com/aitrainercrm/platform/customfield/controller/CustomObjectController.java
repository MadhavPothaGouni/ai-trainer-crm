package com.aitrainercrm.platform.customfield.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.customfield.dto.CreateCustomObjectRecordRequest;
import com.aitrainercrm.platform.customfield.dto.CreateCustomObjectRequest;
import com.aitrainercrm.platform.customfield.dto.CustomObjectDto;
import com.aitrainercrm.platform.customfield.dto.CustomObjectRecordDto;
import com.aitrainercrm.platform.customfield.dto.UpdateCustomObjectRecordRequest;
import com.aitrainercrm.platform.customfield.dto.UpdateCustomObjectRequest;
import com.aitrainercrm.platform.customfield.entity.CustomObject;
import com.aitrainercrm.platform.customfield.entity.CustomObjectRecord;
import com.aitrainercrm.platform.customfield.service.CustomObjectService;
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

/**
 * Custom object definitions plus their records. CUSTOM_OBJECT was seeded
 * ORGANIZATION-scope-only in V2 (see V10's migration comment) - every gate
 * here is a single authority, the same pattern {@code WebhookSubscriptionController}
 * uses for INTEGRATION. Records ride on the same CUSTOM_OBJECT permission
 * rather than getting their own resource - there's no separate "custom
 * object record" row in the permission catalog, records are just data
 * inside a custom object.
 */
@RestController
@RequestMapping("/api/v1/custom-objects")
@RequiredArgsConstructor
public class CustomObjectController {

    private final CustomObjectService customObjectService;

    @GetMapping
    @PreAuthorize("hasAuthority('CUSTOM_OBJECT:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<CustomObjectDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<CustomObject> page = customObjectService.list(principal, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(CustomObjectDto::from).toList()));
    }

    @GetMapping("/{customObjectId}")
    @PreAuthorize("hasAuthority('CUSTOM_OBJECT:READ:ORGANIZATION')")
    public ApiResponse<CustomObjectDto> get(@PathVariable UUID customObjectId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(CustomObjectDto.from(customObjectService.get(principal, customObjectId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CUSTOM_OBJECT:CREATE:ORGANIZATION')")
    public ApiResponse<CustomObjectDto> create(
            @Valid @RequestBody CreateCustomObjectRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(CustomObjectDto.from(customObjectService.create(principal, request)), "Custom object created");
    }

    @PutMapping("/{customObjectId}")
    @PreAuthorize("hasAuthority('CUSTOM_OBJECT:UPDATE:ORGANIZATION')")
    public ApiResponse<CustomObjectDto> update(
            @PathVariable UUID customObjectId,
            @Valid @RequestBody UpdateCustomObjectRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(CustomObjectDto.from(customObjectService.update(principal, customObjectId, request)), "Custom object updated");
    }

    @DeleteMapping("/{customObjectId}")
    @PreAuthorize("hasAuthority('CUSTOM_OBJECT:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID customObjectId, @AuthenticationPrincipal UserPrincipal principal) {
        customObjectService.delete(principal, customObjectId);
        return ApiResponse.ok(null, "Custom object deleted");
    }

    @GetMapping("/{customObjectId}/records")
    @PreAuthorize("hasAuthority('CUSTOM_OBJECT:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<CustomObjectRecordDto>> listRecords(
            @PathVariable UUID customObjectId, Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<CustomObjectRecord> page = customObjectService.listRecords(principal, customObjectId, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(CustomObjectRecordDto::from).toList()));
    }

    @PostMapping("/{customObjectId}/records")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CUSTOM_OBJECT:CREATE:ORGANIZATION')")
    public ApiResponse<CustomObjectRecordDto> createRecord(
            @PathVariable UUID customObjectId,
            @Valid @RequestBody CreateCustomObjectRecordRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(
                CustomObjectRecordDto.from(customObjectService.createRecord(principal, customObjectId, request)), "Record created");
    }

    @PutMapping("/{customObjectId}/records/{recordId}")
    @PreAuthorize("hasAuthority('CUSTOM_OBJECT:UPDATE:ORGANIZATION')")
    public ApiResponse<CustomObjectRecordDto> updateRecord(
            @PathVariable UUID customObjectId,
            @PathVariable UUID recordId,
            @Valid @RequestBody UpdateCustomObjectRecordRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(
                CustomObjectRecordDto.from(customObjectService.updateRecord(principal, customObjectId, recordId, request)),
                "Record updated");
    }

    @DeleteMapping("/{customObjectId}/records/{recordId}")
    @PreAuthorize("hasAuthority('CUSTOM_OBJECT:DELETE:ORGANIZATION')")
    public ApiResponse<Void> deleteRecord(
            @PathVariable UUID customObjectId, @PathVariable UUID recordId, @AuthenticationPrincipal UserPrincipal principal) {
        customObjectService.deleteRecord(principal, customObjectId, recordId);
        return ApiResponse.ok(null, "Record deleted");
    }
}
