package com.aitrainercrm.platform.apikey.controller;

import com.aitrainercrm.platform.apikey.dto.ApiKeyDto;
import com.aitrainercrm.platform.apikey.dto.CreateApiKeyRequest;
import com.aitrainercrm.platform.apikey.service.ApiKeyService;
import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.PageResponse;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * API_KEY is seeded ORGANIZATION-scope-only in V2 (platform administration,
 * same bucket as USER/ROLE/INTEGRATION) - there's no OWN/TEAM/DEPARTMENT
 * variant, so every gate here is a single authority, not the usual
 * {@code hasAnyAuthority} list across four scopes.
 */
@RestController
@RequestMapping("/api/v1/api-keys")
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    @GetMapping
    @PreAuthorize("hasAuthority('API_KEY:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<ApiKeyDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<ApiKeyDto> page = apiKeyService.list(principal, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent()));
    }

    /** The only response, ever, that includes {@code data.rawKey} - copy it now, it can't be shown again. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('API_KEY:CREATE:ORGANIZATION')")
    public ApiResponse<ApiKeyDto> create(@Valid @RequestBody CreateApiKeyRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(apiKeyService.create(principal, request), "API key created - copy the key now, it won't be shown again");
    }

    @DeleteMapping("/{apiKeyId}")
    @PreAuthorize("hasAuthority('API_KEY:DELETE:ORGANIZATION')")
    public ApiResponse<Void> revoke(@PathVariable UUID apiKeyId, @AuthenticationPrincipal UserPrincipal principal) {
        apiKeyService.revoke(principal, apiKeyId);
        return ApiResponse.ok(null, "API key revoked");
    }
}
