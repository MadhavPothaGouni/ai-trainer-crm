package com.aitrainercrm.platform.attachment.controller;

import com.aitrainercrm.platform.attachment.dto.AttachmentDto;
import com.aitrainercrm.platform.attachment.dto.DownloadedFile;
import com.aitrainercrm.platform.attachment.dto.UpdateAttachmentRequest;
import com.aitrainercrm.platform.attachment.entity.Attachment;
import com.aitrainercrm.platform.attachment.service.AttachmentService;
import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.AssignOwnerRequest;
import com.aitrainercrm.platform.common.dto.PageResponse;
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
import org.springframework.web.multipart.MultipartFile;

/** Mirrors EmailMessageController's shape, plus the multipart upload/binary download pair a file-attachment module needs that no earlier module did. */
@RestController
@RequestMapping("/api/v1/attachments")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ATTACHMENT:READ:OWN','ATTACHMENT:READ:TEAM','ATTACHMENT:READ:DEPARTMENT','ATTACHMENT:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<AttachmentDto>> list(
            @RequestParam(required = false) Attachment.RelatedToType relatedToType,
            @RequestParam(required = false) UUID relatedToId,
            Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal) {
        Page<Attachment> page = attachmentService.list(principal, relatedToType, relatedToId, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(AttachmentDto::from).toList()));
    }

    @GetMapping("/export")
    @PreAuthorize("hasAnyAuthority('ATTACHMENT:EXPORT:OWN','ATTACHMENT:EXPORT:TEAM','ATTACHMENT:EXPORT:DEPARTMENT','ATTACHMENT:EXPORT:ORGANIZATION')")
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal UserPrincipal principal) {
        byte[] csv = attachmentService.exportCsv(principal);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("attachments.csv").build().toString())
                .body(csv);
    }

    @GetMapping("/{attachmentId}")
    @PreAuthorize("hasAnyAuthority('ATTACHMENT:READ:OWN','ATTACHMENT:READ:TEAM','ATTACHMENT:READ:DEPARTMENT','ATTACHMENT:READ:ORGANIZATION')")
    public ApiResponse<AttachmentDto> get(@PathVariable UUID attachmentId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(AttachmentDto.from(attachmentService.get(principal, attachmentId)));
    }

    @GetMapping("/{attachmentId}/download")
    @PreAuthorize("hasAnyAuthority('ATTACHMENT:READ:OWN','ATTACHMENT:READ:TEAM','ATTACHMENT:READ:DEPARTMENT','ATTACHMENT:READ:ORGANIZATION')")
    public ResponseEntity<byte[]> download(@PathVariable UUID attachmentId, @AuthenticationPrincipal UserPrincipal principal) {
        DownloadedFile file = attachmentService.download(principal, attachmentId);
        MediaType mediaType = file.contentType() != null ? MediaType.parseMediaType(file.contentType()) : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.fileName()).build().toString())
                .body(file.content());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('ATTACHMENT:CREATE:OWN','ATTACHMENT:CREATE:TEAM','ATTACHMENT:CREATE:DEPARTMENT','ATTACHMENT:CREATE:ORGANIZATION')")
    public ApiResponse<AttachmentDto> create(
            @RequestParam("file") MultipartFile file,
            @RequestParam Attachment.RelatedToType relatedToType,
            @RequestParam UUID relatedToId,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) UUID ownerId,
            @AuthenticationPrincipal UserPrincipal principal) {
        Attachment attachment = attachmentService.create(principal, file, relatedToType, relatedToId, description, ownerId);
        return ApiResponse.ok(AttachmentDto.from(attachment), "Attachment uploaded");
    }

    @PutMapping("/{attachmentId}")
    @PreAuthorize("hasAnyAuthority('ATTACHMENT:UPDATE:OWN','ATTACHMENT:UPDATE:TEAM','ATTACHMENT:UPDATE:DEPARTMENT','ATTACHMENT:UPDATE:ORGANIZATION')")
    public ApiResponse<AttachmentDto> update(
            @PathVariable UUID attachmentId, @Valid @RequestBody UpdateAttachmentRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(AttachmentDto.from(attachmentService.update(principal, attachmentId, request)), "Attachment updated");
    }

    @DeleteMapping("/{attachmentId}")
    @PreAuthorize("hasAnyAuthority('ATTACHMENT:DELETE:OWN','ATTACHMENT:DELETE:TEAM','ATTACHMENT:DELETE:DEPARTMENT','ATTACHMENT:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID attachmentId, @AuthenticationPrincipal UserPrincipal principal) {
        attachmentService.delete(principal, attachmentId);
        return ApiResponse.ok(null, "Attachment deleted");
    }

    @PatchMapping("/{attachmentId}/owner")
    @PreAuthorize("hasAnyAuthority('ATTACHMENT:ASSIGN:OWN','ATTACHMENT:ASSIGN:TEAM','ATTACHMENT:ASSIGN:DEPARTMENT','ATTACHMENT:ASSIGN:ORGANIZATION')")
    public ApiResponse<AttachmentDto> assignOwner(
            @PathVariable UUID attachmentId, @Valid @RequestBody AssignOwnerRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(AttachmentDto.from(attachmentService.assignOwner(principal, attachmentId, request.ownerId())), "Owner updated");
    }
}
