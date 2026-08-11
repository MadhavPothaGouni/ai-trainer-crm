package com.aitrainercrm.platform.email.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.AssignOwnerRequest;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.email.dto.EmailMessageDto;
import com.aitrainercrm.platform.email.dto.LogEmailRequest;
import com.aitrainercrm.platform.email.entity.EmailMessage;
import com.aitrainercrm.platform.email.service.EmailMessageService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Mirrors TicketController's shape, plus ActivityController's relatedToType/relatedToId list filter - see EmailMessageService's javadoc for why. */
@RestController
@RequestMapping("/api/v1/email-messages")
@RequiredArgsConstructor
public class EmailMessageController {

    private final EmailMessageService emailMessageService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('EMAIL_MESSAGE:READ:OWN','EMAIL_MESSAGE:READ:TEAM','EMAIL_MESSAGE:READ:DEPARTMENT','EMAIL_MESSAGE:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<EmailMessageDto>> list(
            @RequestParam(required = false) EmailMessage.RelatedToType relatedToType,
            @RequestParam(required = false) UUID relatedToId,
            Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal) {
        Page<EmailMessage> page = emailMessageService.list(principal, relatedToType, relatedToId, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(EmailMessageDto::from).toList()));
    }

    @GetMapping("/export")
    @PreAuthorize("hasAnyAuthority('EMAIL_MESSAGE:EXPORT:OWN','EMAIL_MESSAGE:EXPORT:TEAM','EMAIL_MESSAGE:EXPORT:DEPARTMENT','EMAIL_MESSAGE:EXPORT:ORGANIZATION')")
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal UserPrincipal principal) {
        byte[] csv = emailMessageService.exportCsv(principal);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("email-messages.csv").build().toString())
                .body(csv);
    }

    @GetMapping("/{emailId}")
    @PreAuthorize("hasAnyAuthority('EMAIL_MESSAGE:READ:OWN','EMAIL_MESSAGE:READ:TEAM','EMAIL_MESSAGE:READ:DEPARTMENT','EMAIL_MESSAGE:READ:ORGANIZATION')")
    public ApiResponse<EmailMessageDto> get(@PathVariable UUID emailId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(EmailMessageDto.from(emailMessageService.get(principal, emailId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('EMAIL_MESSAGE:CREATE:OWN','EMAIL_MESSAGE:CREATE:TEAM','EMAIL_MESSAGE:CREATE:DEPARTMENT','EMAIL_MESSAGE:CREATE:ORGANIZATION')")
    public ApiResponse<EmailMessageDto> create(@Valid @RequestBody LogEmailRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(EmailMessageDto.from(emailMessageService.create(principal, request)), "Email logged");
    }

    @PutMapping("/{emailId}")
    @PreAuthorize("hasAnyAuthority('EMAIL_MESSAGE:UPDATE:OWN','EMAIL_MESSAGE:UPDATE:TEAM','EMAIL_MESSAGE:UPDATE:DEPARTMENT','EMAIL_MESSAGE:UPDATE:ORGANIZATION')")
    public ApiResponse<EmailMessageDto> update(
            @PathVariable UUID emailId, @Valid @RequestBody LogEmailRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(EmailMessageDto.from(emailMessageService.update(principal, emailId, request)), "Email updated");
    }

    @DeleteMapping("/{emailId}")
    @PreAuthorize("hasAnyAuthority('EMAIL_MESSAGE:DELETE:OWN','EMAIL_MESSAGE:DELETE:TEAM','EMAIL_MESSAGE:DELETE:DEPARTMENT','EMAIL_MESSAGE:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID emailId, @AuthenticationPrincipal UserPrincipal principal) {
        emailMessageService.delete(principal, emailId);
        return ApiResponse.ok(null, "Email deleted");
    }

    @PatchMapping("/{emailId}/owner")
    @PreAuthorize("hasAnyAuthority('EMAIL_MESSAGE:ASSIGN:OWN','EMAIL_MESSAGE:ASSIGN:TEAM','EMAIL_MESSAGE:ASSIGN:DEPARTMENT','EMAIL_MESSAGE:ASSIGN:ORGANIZATION')")
    public ApiResponse<EmailMessageDto> assignOwner(
            @PathVariable UUID emailId, @Valid @RequestBody AssignOwnerRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(EmailMessageDto.from(emailMessageService.assignOwner(principal, emailId, request.ownerId())), "Owner updated");
    }
}
