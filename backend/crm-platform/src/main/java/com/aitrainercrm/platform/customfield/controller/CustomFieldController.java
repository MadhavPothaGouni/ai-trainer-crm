package com.aitrainercrm.platform.customfield.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.exception.BusinessException;
import com.aitrainercrm.platform.customfield.dto.CreateCustomFieldRequest;
import com.aitrainercrm.platform.customfield.dto.CustomFieldDto;
import com.aitrainercrm.platform.customfield.dto.CustomFieldValueDto;
import com.aitrainercrm.platform.customfield.dto.SetCustomFieldValuesRequest;
import com.aitrainercrm.platform.customfield.dto.UpdateCustomFieldRequest;
import com.aitrainercrm.platform.customfield.entity.CustomField;
import com.aitrainercrm.platform.customfield.service.CustomFieldService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Custom field definitions plus the get/set-value endpoints for one record
 * at a time. CUSTOM_FIELD was seeded ORGANIZATION-scope-only in V2 (see
 * V10's migration comment) - every gate here is a single authority.
 *
 * <p>{@code /values} deliberately doesn't live under
 * {@code /custom-objects/.../records/{id}} or under each standard entity's
 * own controller (e.g. {@code AccountController}) - keeping it here means
 * one implementation serves both kinds of target, at the cost of gating
 * value reads/writes on CUSTOM_FIELD:READ/UPDATE rather than on, say,
 * ACCOUNT:UPDATE when the target is a standard entity. That's a deliberate,
 * documented simplification (an admin who can define custom fields can
 * also fill them in on any record), not an oversight.
 */
@RestController
@RequestMapping("/api/v1/custom-fields")
@RequiredArgsConstructor
public class CustomFieldController {

    private final CustomFieldService customFieldService;

    @GetMapping
    @PreAuthorize("hasAuthority('CUSTOM_FIELD:READ:ORGANIZATION')")
    public ApiResponse<List<CustomFieldDto>> list(
            @RequestParam(required = false) CustomField.StandardEntityType standardEntityType,
            @RequestParam(required = false) UUID customObjectId,
            @AuthenticationPrincipal UserPrincipal principal) {
        List<CustomField> fields = resolveFields(principal, standardEntityType, customObjectId);
        return ApiResponse.ok(fields.stream().map(CustomFieldDto::from).toList());
    }

    @GetMapping("/{fieldId}")
    @PreAuthorize("hasAuthority('CUSTOM_FIELD:READ:ORGANIZATION')")
    public ApiResponse<CustomFieldDto> get(@PathVariable UUID fieldId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(CustomFieldDto.from(customFieldService.get(principal, fieldId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CUSTOM_FIELD:CREATE:ORGANIZATION')")
    public ApiResponse<CustomFieldDto> create(
            @Valid @RequestBody CreateCustomFieldRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(CustomFieldDto.from(customFieldService.create(principal, request)), "Custom field created");
    }

    @PutMapping("/{fieldId}")
    @PreAuthorize("hasAuthority('CUSTOM_FIELD:UPDATE:ORGANIZATION')")
    public ApiResponse<CustomFieldDto> update(
            @PathVariable UUID fieldId,
            @Valid @RequestBody UpdateCustomFieldRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(CustomFieldDto.from(customFieldService.update(principal, fieldId, request)), "Custom field updated");
    }

    @DeleteMapping("/{fieldId}")
    @PreAuthorize("hasAuthority('CUSTOM_FIELD:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID fieldId, @AuthenticationPrincipal UserPrincipal principal) {
        customFieldService.delete(principal, fieldId);
        return ApiResponse.ok(null, "Custom field deleted");
    }

    @GetMapping("/values")
    @PreAuthorize("hasAuthority('CUSTOM_FIELD:READ:ORGANIZATION')")
    public ApiResponse<List<CustomFieldValueDto>> getValues(
            @RequestParam(required = false) CustomField.StandardEntityType standardEntityType,
            @RequestParam(required = false) UUID customObjectId,
            @RequestParam UUID recordId,
            @AuthenticationPrincipal UserPrincipal principal) {
        List<CustomField> fields = resolveFields(principal, standardEntityType, customObjectId);
        return ApiResponse.ok(customFieldService.getValues(principal, fields, recordId));
    }

    @PutMapping("/values")
    @PreAuthorize("hasAuthority('CUSTOM_FIELD:UPDATE:ORGANIZATION')")
    public ApiResponse<List<CustomFieldValueDto>> setValues(
            @RequestParam(required = false) CustomField.StandardEntityType standardEntityType,
            @RequestParam(required = false) UUID customObjectId,
            @RequestParam UUID recordId,
            @Valid @RequestBody SetCustomFieldValuesRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        List<CustomField> fields = resolveFields(principal, standardEntityType, customObjectId);
        return ApiResponse.ok(customFieldService.setValues(principal, fields, recordId, request), "Custom field values saved");
    }

    private List<CustomField> resolveFields(
            UserPrincipal principal, CustomField.StandardEntityType standardEntityType, UUID customObjectId) {
        boolean hasStandard = standardEntityType != null;
        boolean hasObject = customObjectId != null;
        if (hasStandard == hasObject) {
            throw new BusinessException(
                    "CUSTOM_FIELD_INVALID_TARGET",
                    "Exactly one of standardEntityType or customObjectId query params must be set",
                    HttpStatus.BAD_REQUEST);
        }
        return hasStandard
                ? customFieldService.listForStandardEntity(principal, standardEntityType)
                : customFieldService.listForCustomObject(principal, customObjectId);
    }
}
