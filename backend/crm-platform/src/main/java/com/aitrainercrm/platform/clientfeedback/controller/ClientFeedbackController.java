package com.aitrainercrm.platform.clientfeedback.controller;

import com.aitrainercrm.platform.clientfeedback.dto.ClientFeedbackDto;
import com.aitrainercrm.platform.clientfeedback.dto.CreateClientFeedbackRequest;
import com.aitrainercrm.platform.clientfeedback.dto.UpdateClientFeedbackRequest;
import com.aitrainercrm.platform.clientfeedback.entity.ClientFeedback;
import com.aitrainercrm.platform.clientfeedback.service.ClientFeedbackService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Standard CRUD, no status endpoint - a submitted rating is a point-in-time fact, mirrors NutritionLogController's shape. */
@RestController
@RequestMapping("/api/v1/client-feedback")
@RequiredArgsConstructor
public class ClientFeedbackController {

    private final ClientFeedbackService clientFeedbackService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('CLIENT_FEEDBACK:READ:OWN','CLIENT_FEEDBACK:READ:TEAM','CLIENT_FEEDBACK:READ:DEPARTMENT','CLIENT_FEEDBACK:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<ClientFeedbackDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<ClientFeedback> page = clientFeedbackService.list(principal, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(ClientFeedbackDto::from).toList()));
    }

    @GetMapping("/{clientFeedbackId}")
    @PreAuthorize("hasAnyAuthority('CLIENT_FEEDBACK:READ:OWN','CLIENT_FEEDBACK:READ:TEAM','CLIENT_FEEDBACK:READ:DEPARTMENT','CLIENT_FEEDBACK:READ:ORGANIZATION')")
    public ApiResponse<ClientFeedbackDto> get(@PathVariable UUID clientFeedbackId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(ClientFeedbackDto.from(clientFeedbackService.get(principal, clientFeedbackId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('CLIENT_FEEDBACK:CREATE:OWN','CLIENT_FEEDBACK:CREATE:TEAM','CLIENT_FEEDBACK:CREATE:DEPARTMENT','CLIENT_FEEDBACK:CREATE:ORGANIZATION')")
    public ApiResponse<ClientFeedbackDto> create(
            @Valid @RequestBody CreateClientFeedbackRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(ClientFeedbackDto.from(clientFeedbackService.create(principal, request)), "Feedback recorded");
    }

    @PutMapping("/{clientFeedbackId}")
    @PreAuthorize("hasAnyAuthority('CLIENT_FEEDBACK:UPDATE:OWN','CLIENT_FEEDBACK:UPDATE:TEAM','CLIENT_FEEDBACK:UPDATE:DEPARTMENT','CLIENT_FEEDBACK:UPDATE:ORGANIZATION')")
    public ApiResponse<ClientFeedbackDto> update(
            @PathVariable UUID clientFeedbackId,
            @Valid @RequestBody UpdateClientFeedbackRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(ClientFeedbackDto.from(clientFeedbackService.update(principal, clientFeedbackId, request)), "Feedback updated");
    }

    @DeleteMapping("/{clientFeedbackId}")
    @PreAuthorize("hasAnyAuthority('CLIENT_FEEDBACK:DELETE:OWN','CLIENT_FEEDBACK:DELETE:TEAM','CLIENT_FEEDBACK:DELETE:DEPARTMENT','CLIENT_FEEDBACK:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID clientFeedbackId, @AuthenticationPrincipal UserPrincipal principal) {
        clientFeedbackService.delete(principal, clientFeedbackId);
        return ApiResponse.ok(null, "Feedback deleted");
    }
}
