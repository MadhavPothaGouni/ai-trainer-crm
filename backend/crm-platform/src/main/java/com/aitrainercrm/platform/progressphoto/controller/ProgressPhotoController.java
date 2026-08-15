package com.aitrainercrm.platform.progressphoto.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.progressphoto.dto.CreateProgressPhotoRequest;
import com.aitrainercrm.platform.progressphoto.dto.ProgressPhotoDto;
import com.aitrainercrm.platform.progressphoto.dto.UpdateProgressPhotoRequest;
import com.aitrainercrm.platform.progressphoto.entity.ProgressPhoto;
import com.aitrainercrm.platform.progressphoto.service.ProgressPhotoService;
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

/** No status PATCH endpoint - see ProgressPhoto's javadoc for why progress photos have no status lifecycle. */
@RestController
@RequestMapping("/api/v1/progress-photos")
@RequiredArgsConstructor
public class ProgressPhotoController {

    private final ProgressPhotoService progressPhotoService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('PROGRESS_PHOTO:READ:OWN','PROGRESS_PHOTO:READ:TEAM','PROGRESS_PHOTO:READ:DEPARTMENT','PROGRESS_PHOTO:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<ProgressPhotoDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<ProgressPhoto> page = progressPhotoService.list(principal, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(ProgressPhotoDto::from).toList()));
    }

    @GetMapping("/{progressPhotoId}")
    @PreAuthorize("hasAnyAuthority('PROGRESS_PHOTO:READ:OWN','PROGRESS_PHOTO:READ:TEAM','PROGRESS_PHOTO:READ:DEPARTMENT','PROGRESS_PHOTO:READ:ORGANIZATION')")
    public ApiResponse<ProgressPhotoDto> get(@PathVariable UUID progressPhotoId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(ProgressPhotoDto.from(progressPhotoService.get(principal, progressPhotoId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('PROGRESS_PHOTO:CREATE:OWN','PROGRESS_PHOTO:CREATE:TEAM','PROGRESS_PHOTO:CREATE:DEPARTMENT','PROGRESS_PHOTO:CREATE:ORGANIZATION')")
    public ApiResponse<ProgressPhotoDto> create(
            @Valid @RequestBody CreateProgressPhotoRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(ProgressPhotoDto.from(progressPhotoService.create(principal, request)), "Progress photo logged");
    }

    @PutMapping("/{progressPhotoId}")
    @PreAuthorize("hasAnyAuthority('PROGRESS_PHOTO:UPDATE:OWN','PROGRESS_PHOTO:UPDATE:TEAM','PROGRESS_PHOTO:UPDATE:DEPARTMENT','PROGRESS_PHOTO:UPDATE:ORGANIZATION')")
    public ApiResponse<ProgressPhotoDto> update(
            @PathVariable UUID progressPhotoId,
            @Valid @RequestBody UpdateProgressPhotoRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(ProgressPhotoDto.from(progressPhotoService.update(principal, progressPhotoId, request)), "Progress photo updated");
    }

    @DeleteMapping("/{progressPhotoId}")
    @PreAuthorize("hasAnyAuthority('PROGRESS_PHOTO:DELETE:OWN','PROGRESS_PHOTO:DELETE:TEAM','PROGRESS_PHOTO:DELETE:DEPARTMENT','PROGRESS_PHOTO:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID progressPhotoId, @AuthenticationPrincipal UserPrincipal principal) {
        progressPhotoService.delete(principal, progressPhotoId);
        return ApiResponse.ok(null, "Progress photo deleted");
    }
}
