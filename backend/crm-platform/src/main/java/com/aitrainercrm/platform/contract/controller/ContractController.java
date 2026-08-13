package com.aitrainercrm.platform.contract.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.contract.dto.ContractDto;
import com.aitrainercrm.platform.contract.dto.CreateContractRequest;
import com.aitrainercrm.platform.contract.dto.UpdateContractRequest;
import com.aitrainercrm.platform.contract.dto.UpdateContractStatusRequest;
import com.aitrainercrm.platform.contract.entity.Contract;
import com.aitrainercrm.platform.contract.service.ContractService;
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

/** Mirrors TicketController's shape exactly - see TicketController's own javadoc for the reasoning behind the coarse-@PreAuthorize-then-service-layer-record-check split. */
@RestController
@RequestMapping("/api/v1/contracts")
@RequiredArgsConstructor
public class ContractController {

    private final ContractService contractService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('CONTRACT:READ:OWN','CONTRACT:READ:TEAM','CONTRACT:READ:DEPARTMENT','CONTRACT:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<ContractDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<Contract> page = contractService.list(principal, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(ContractDto::from).toList()));
    }

    @GetMapping("/{contractId}")
    @PreAuthorize("hasAnyAuthority('CONTRACT:READ:OWN','CONTRACT:READ:TEAM','CONTRACT:READ:DEPARTMENT','CONTRACT:READ:ORGANIZATION')")
    public ApiResponse<ContractDto> get(@PathVariable UUID contractId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(ContractDto.from(contractService.get(principal, contractId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('CONTRACT:CREATE:OWN','CONTRACT:CREATE:TEAM','CONTRACT:CREATE:DEPARTMENT','CONTRACT:CREATE:ORGANIZATION')")
    public ApiResponse<ContractDto> create(@Valid @RequestBody CreateContractRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(ContractDto.from(contractService.create(principal, request)), "Contract created");
    }

    @PutMapping("/{contractId}")
    @PreAuthorize("hasAnyAuthority('CONTRACT:UPDATE:OWN','CONTRACT:UPDATE:TEAM','CONTRACT:UPDATE:DEPARTMENT','CONTRACT:UPDATE:ORGANIZATION')")
    public ApiResponse<ContractDto> update(
            @PathVariable UUID contractId, @Valid @RequestBody UpdateContractRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(ContractDto.from(contractService.update(principal, contractId, request)), "Contract updated");
    }

    @PatchMapping("/{contractId}/status")
    @PreAuthorize("hasAnyAuthority('CONTRACT:UPDATE:OWN','CONTRACT:UPDATE:TEAM','CONTRACT:UPDATE:DEPARTMENT','CONTRACT:UPDATE:ORGANIZATION')")
    public ApiResponse<ContractDto> updateStatus(
            @PathVariable UUID contractId, @Valid @RequestBody UpdateContractStatusRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(ContractDto.from(contractService.updateStatus(principal, contractId, request.status())), "Status updated");
    }

    @DeleteMapping("/{contractId}")
    @PreAuthorize("hasAnyAuthority('CONTRACT:DELETE:OWN','CONTRACT:DELETE:TEAM','CONTRACT:DELETE:DEPARTMENT','CONTRACT:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID contractId, @AuthenticationPrincipal UserPrincipal principal) {
        contractService.delete(principal, contractId);
        return ApiResponse.ok(null, "Contract deleted");
    }
}
