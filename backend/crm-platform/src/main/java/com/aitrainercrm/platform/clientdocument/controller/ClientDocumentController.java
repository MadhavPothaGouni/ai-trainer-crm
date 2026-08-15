package com.aitrainercrm.platform.clientdocument.controller;

import com.aitrainercrm.platform.clientdocument.dto.ClientDocumentDto;
import com.aitrainercrm.platform.clientdocument.dto.CreateClientDocumentRequest;
import com.aitrainercrm.platform.clientdocument.dto.UpdateClientDocumentRequest;
import com.aitrainercrm.platform.clientdocument.dto.UpdateClientDocumentStatusRequest;
import com.aitrainercrm.platform.clientdocument.entity.ClientDocument;
import com.aitrainercrm.platform.clientdocument.service.ClientDocumentService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Mirrors ClientGoalController/ReferralController's shape exactly, including the separate PATCH .../status endpoint for signedAt stamping. */
@RestController
@RequestMapping("/api/v1/client-documents")
@RequiredArgsConstructor
public class ClientDocumentController {

    private final ClientDocumentService clientDocumentService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('CLIENT_DOCUMENT:READ:OWN','CLIENT_DOCUMENT:READ:TEAM','CLIENT_DOCUMENT:READ:DEPARTMENT','CLIENT_DOCUMENT:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<ClientDocumentDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<ClientDocument> page = clientDocumentService.list(principal, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(ClientDocumentDto::from).toList()));
    }

    @GetMapping("/{clientDocumentId}")
    @PreAuthorize("hasAnyAuthority('CLIENT_DOCUMENT:READ:OWN','CLIENT_DOCUMENT:READ:TEAM','CLIENT_DOCUMENT:READ:DEPARTMENT','CLIENT_DOCUMENT:READ:ORGANIZATION')")
    public ApiResponse<ClientDocumentDto> get(@PathVariable UUID clientDocumentId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(ClientDocumentDto.from(clientDocumentService.get(principal, clientDocumentId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('CLIENT_DOCUMENT:CREATE:OWN','CLIENT_DOCUMENT:CREATE:TEAM','CLIENT_DOCUMENT:CREATE:DEPARTMENT','CLIENT_DOCUMENT:CREATE:ORGANIZATION')")
    public ApiResponse<ClientDocumentDto> create(
            @Valid @RequestBody CreateClientDocumentRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(ClientDocumentDto.from(clientDocumentService.create(principal, request)), "Document created");
    }

    @PutMapping("/{clientDocumentId}")
    @PreAuthorize("hasAnyAuthority('CLIENT_DOCUMENT:UPDATE:OWN','CLIENT_DOCUMENT:UPDATE:TEAM','CLIENT_DOCUMENT:UPDATE:DEPARTMENT','CLIENT_DOCUMENT:UPDATE:ORGANIZATION')")
    public ApiResponse<ClientDocumentDto> update(
            @PathVariable UUID clientDocumentId,
            @Valid @RequestBody UpdateClientDocumentRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(ClientDocumentDto.from(clientDocumentService.update(principal, clientDocumentId, request)), "Document updated");
    }

    @PatchMapping("/{clientDocumentId}/status")
    @PreAuthorize("hasAnyAuthority('CLIENT_DOCUMENT:UPDATE:OWN','CLIENT_DOCUMENT:UPDATE:TEAM','CLIENT_DOCUMENT:UPDATE:DEPARTMENT','CLIENT_DOCUMENT:UPDATE:ORGANIZATION')")
    public ApiResponse<ClientDocumentDto> updateStatus(
            @PathVariable UUID clientDocumentId,
            @Valid @RequestBody UpdateClientDocumentStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(
                ClientDocumentDto.from(clientDocumentService.updateStatus(principal, clientDocumentId, request.status())), "Status updated");
    }

    @DeleteMapping("/{clientDocumentId}")
    @PreAuthorize("hasAnyAuthority('CLIENT_DOCUMENT:DELETE:OWN','CLIENT_DOCUMENT:DELETE:TEAM','CLIENT_DOCUMENT:DELETE:DEPARTMENT','CLIENT_DOCUMENT:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID clientDocumentId, @AuthenticationPrincipal UserPrincipal principal) {
        clientDocumentService.delete(principal, clientDocumentId);
        return ApiResponse.ok(null, "Document deleted");
    }
}
