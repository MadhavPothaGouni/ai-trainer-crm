package com.aitrainercrm.platform.gdpr.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.gdpr.dto.CreateDataSubjectRequest;
import com.aitrainercrm.platform.gdpr.dto.DataSubjectExportDto;
import com.aitrainercrm.platform.gdpr.dto.DataSubjectRequestDto;
import com.aitrainercrm.platform.gdpr.entity.DataSubjectRequest;
import com.aitrainercrm.platform.gdpr.service.DataSubjectRequestService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GDPR/CCPA-style data-subject rights: export or erase every Contact/Lead in this organization
 * matching one email address. See V30's migration comment for the full design.
 *
 * <p>{@link #export} follows the exact convention {@code ImportExportController}'s CSV exports
 * already established - a raw {@code ResponseEntity<byte[]>} download, not the usual {@code
 * ApiResponse} envelope - just JSON instead of CSV, since the export payload is a nested tree
 * (contacts + leads), not a flat table. {@link #erase} returns a normal {@code ApiResponse} since
 * its result (affected-row counts) is data a UI renders, not a file.
 */
@RestController
@RequestMapping("/api/v1/data-subject-requests")
@RequiredArgsConstructor
public class DataSubjectRequestController {

    private final DataSubjectRequestService dataSubjectRequestService;
    private final ObjectMapper objectMapper;

    @PostMapping("/export")
    @PreAuthorize("hasAuthority('DATA_SUBJECT_REQUEST:EXPORT:ORGANIZATION')")
    public ResponseEntity<byte[]> export(
            @Valid @RequestBody CreateDataSubjectRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        DataSubjectExportDto export = dataSubjectRequestService.export(principal, request.subjectEmail());
        byte[] json = writeJson(export);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("data-subject-export.json").build().toString())
                .body(json);
    }

    @PostMapping("/erase")
    @PreAuthorize("hasAuthority('DATA_SUBJECT_REQUEST:DELETE:ORGANIZATION')")
    public ApiResponse<DataSubjectRequestDto> erase(
            @Valid @RequestBody CreateDataSubjectRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        DataSubjectRequest result = dataSubjectRequestService.erase(principal, request.subjectEmail());
        return ApiResponse.ok(
                DataSubjectRequestDto.from(result),
                "Erased %d contact(s) and %d lead(s)".formatted(result.getContactsAffected(), result.getLeadsAffected()));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('DATA_SUBJECT_REQUEST:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<DataSubjectRequestDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<DataSubjectRequest> page = dataSubjectRequestService.list(principal.getOrganizationId(), pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(DataSubjectRequestDto::from).toList()));
    }

    @GetMapping("/{requestId}")
    @PreAuthorize("hasAuthority('DATA_SUBJECT_REQUEST:READ:ORGANIZATION')")
    public ApiResponse<DataSubjectRequestDto> get(@PathVariable UUID requestId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(DataSubjectRequestDto.from(dataSubjectRequestService.get(principal.getOrganizationId(), requestId)));
    }

    private byte[] writeJson(DataSubjectExportDto export) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(export);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize the data subject export", e);
        }
    }
}
