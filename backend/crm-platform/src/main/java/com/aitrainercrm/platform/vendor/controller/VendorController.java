package com.aitrainercrm.platform.vendor.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.vendor.dto.CreateVendorRequest;
import com.aitrainercrm.platform.vendor.dto.UpdateVendorRequest;
import com.aitrainercrm.platform.vendor.dto.VendorDto;
import com.aitrainercrm.platform.vendor.entity.Vendor;
import com.aitrainercrm.platform.vendor.service.VendorService;
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

/** No OWN scope on VENDOR (see VendorService's javadoc) - mirrors EquipmentController exactly. */
@RestController
@RequestMapping("/api/v1/vendors")
@RequiredArgsConstructor
public class VendorController {

    private final VendorService vendorService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('VENDOR:READ:TEAM','VENDOR:READ:DEPARTMENT','VENDOR:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<VendorDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<Vendor> page = vendorService.list(principal, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(VendorDto::from).toList()));
    }

    @GetMapping("/{vendorId}")
    @PreAuthorize("hasAnyAuthority('VENDOR:READ:TEAM','VENDOR:READ:DEPARTMENT','VENDOR:READ:ORGANIZATION')")
    public ApiResponse<VendorDto> get(@PathVariable UUID vendorId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(VendorDto.from(vendorService.get(principal, vendorId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('VENDOR:CREATE:TEAM','VENDOR:CREATE:DEPARTMENT','VENDOR:CREATE:ORGANIZATION')")
    public ApiResponse<VendorDto> create(@Valid @RequestBody CreateVendorRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(VendorDto.from(vendorService.create(principal, request)), "Vendor added");
    }

    @PutMapping("/{vendorId}")
    @PreAuthorize("hasAnyAuthority('VENDOR:UPDATE:TEAM','VENDOR:UPDATE:DEPARTMENT','VENDOR:UPDATE:ORGANIZATION')")
    public ApiResponse<VendorDto> update(
            @PathVariable UUID vendorId, @Valid @RequestBody UpdateVendorRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(VendorDto.from(vendorService.update(principal, vendorId, request)), "Vendor updated");
    }

    @DeleteMapping("/{vendorId}")
    @PreAuthorize("hasAnyAuthority('VENDOR:DELETE:TEAM','VENDOR:DELETE:DEPARTMENT','VENDOR:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID vendorId, @AuthenticationPrincipal UserPrincipal principal) {
        vendorService.delete(principal, vendorId);
        return ApiResponse.ok(null, "Vendor deleted");
    }
}
