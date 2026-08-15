package com.aitrainercrm.platform.exercise.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.exercise.dto.CreatePersonalRecordRequest;
import com.aitrainercrm.platform.exercise.dto.PersonalRecordDto;
import com.aitrainercrm.platform.exercise.dto.UpdatePersonalRecordRequest;
import com.aitrainercrm.platform.exercise.entity.PersonalRecord;
import com.aitrainercrm.platform.exercise.service.PersonalRecordService;
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

/** Standard CRUD, no status endpoint - mirrors ProgressPhotoController's shape. */
@RestController
@RequestMapping("/api/v1/personal-records")
@RequiredArgsConstructor
public class PersonalRecordController {

    private final PersonalRecordService personalRecordService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('PERSONAL_RECORD:READ:OWN','PERSONAL_RECORD:READ:TEAM','PERSONAL_RECORD:READ:DEPARTMENT','PERSONAL_RECORD:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<PersonalRecordDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<PersonalRecord> page = personalRecordService.list(principal, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(PersonalRecordDto::from).toList()));
    }

    @GetMapping("/{personalRecordId}")
    @PreAuthorize("hasAnyAuthority('PERSONAL_RECORD:READ:OWN','PERSONAL_RECORD:READ:TEAM','PERSONAL_RECORD:READ:DEPARTMENT','PERSONAL_RECORD:READ:ORGANIZATION')")
    public ApiResponse<PersonalRecordDto> get(@PathVariable UUID personalRecordId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(PersonalRecordDto.from(personalRecordService.get(principal, personalRecordId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('PERSONAL_RECORD:CREATE:OWN','PERSONAL_RECORD:CREATE:TEAM','PERSONAL_RECORD:CREATE:DEPARTMENT','PERSONAL_RECORD:CREATE:ORGANIZATION')")
    public ApiResponse<PersonalRecordDto> create(
            @Valid @RequestBody CreatePersonalRecordRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(PersonalRecordDto.from(personalRecordService.create(principal, request)), "Personal record logged");
    }

    @PutMapping("/{personalRecordId}")
    @PreAuthorize("hasAnyAuthority('PERSONAL_RECORD:UPDATE:OWN','PERSONAL_RECORD:UPDATE:TEAM','PERSONAL_RECORD:UPDATE:DEPARTMENT','PERSONAL_RECORD:UPDATE:ORGANIZATION')")
    public ApiResponse<PersonalRecordDto> update(
            @PathVariable UUID personalRecordId,
            @Valid @RequestBody UpdatePersonalRecordRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(PersonalRecordDto.from(personalRecordService.update(principal, personalRecordId, request)), "Personal record updated");
    }

    @DeleteMapping("/{personalRecordId}")
    @PreAuthorize("hasAnyAuthority('PERSONAL_RECORD:DELETE:OWN','PERSONAL_RECORD:DELETE:TEAM','PERSONAL_RECORD:DELETE:DEPARTMENT','PERSONAL_RECORD:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID personalRecordId, @AuthenticationPrincipal UserPrincipal principal) {
        personalRecordService.delete(principal, personalRecordId);
        return ApiResponse.ok(null, "Personal record deleted");
    }
}
