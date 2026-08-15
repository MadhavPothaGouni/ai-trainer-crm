package com.aitrainercrm.platform.loyalty.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.loyalty.dto.CreateLoyaltyTransactionRequest;
import com.aitrainercrm.platform.loyalty.dto.LoyaltyBalanceDto;
import com.aitrainercrm.platform.loyalty.dto.LoyaltyTransactionDto;
import com.aitrainercrm.platform.loyalty.dto.UpdateLoyaltyTransactionRequest;
import com.aitrainercrm.platform.loyalty.entity.LoyaltyTransaction;
import com.aitrainercrm.platform.loyalty.service.LoyaltyTransactionService;
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

/** Mirrors ProgressPhotoController's CRUD shape, plus a dedicated GET .../balance/{contactId} aggregate endpoint. */
@RestController
@RequestMapping("/api/v1/loyalty-transactions")
@RequiredArgsConstructor
public class LoyaltyTransactionController {

    private final LoyaltyTransactionService loyaltyTransactionService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('LOYALTY_TRANSACTION:READ:OWN','LOYALTY_TRANSACTION:READ:TEAM','LOYALTY_TRANSACTION:READ:DEPARTMENT','LOYALTY_TRANSACTION:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<LoyaltyTransactionDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<LoyaltyTransaction> page = loyaltyTransactionService.list(principal, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(LoyaltyTransactionDto::from).toList()));
    }

    @GetMapping("/balance/{contactId}")
    @PreAuthorize("hasAnyAuthority('LOYALTY_TRANSACTION:READ:OWN','LOYALTY_TRANSACTION:READ:TEAM','LOYALTY_TRANSACTION:READ:DEPARTMENT','LOYALTY_TRANSACTION:READ:ORGANIZATION')")
    public ApiResponse<LoyaltyBalanceDto> getBalance(@PathVariable UUID contactId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(new LoyaltyBalanceDto(contactId, loyaltyTransactionService.getBalance(principal, contactId)));
    }

    @GetMapping("/{loyaltyTransactionId}")
    @PreAuthorize("hasAnyAuthority('LOYALTY_TRANSACTION:READ:OWN','LOYALTY_TRANSACTION:READ:TEAM','LOYALTY_TRANSACTION:READ:DEPARTMENT','LOYALTY_TRANSACTION:READ:ORGANIZATION')")
    public ApiResponse<LoyaltyTransactionDto> get(@PathVariable UUID loyaltyTransactionId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(LoyaltyTransactionDto.from(loyaltyTransactionService.get(principal, loyaltyTransactionId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('LOYALTY_TRANSACTION:CREATE:OWN','LOYALTY_TRANSACTION:CREATE:TEAM','LOYALTY_TRANSACTION:CREATE:DEPARTMENT','LOYALTY_TRANSACTION:CREATE:ORGANIZATION')")
    public ApiResponse<LoyaltyTransactionDto> create(
            @Valid @RequestBody CreateLoyaltyTransactionRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(LoyaltyTransactionDto.from(loyaltyTransactionService.create(principal, request)), "Loyalty transaction created");
    }

    @PutMapping("/{loyaltyTransactionId}")
    @PreAuthorize("hasAnyAuthority('LOYALTY_TRANSACTION:UPDATE:OWN','LOYALTY_TRANSACTION:UPDATE:TEAM','LOYALTY_TRANSACTION:UPDATE:DEPARTMENT','LOYALTY_TRANSACTION:UPDATE:ORGANIZATION')")
    public ApiResponse<LoyaltyTransactionDto> update(
            @PathVariable UUID loyaltyTransactionId,
            @Valid @RequestBody UpdateLoyaltyTransactionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(
                LoyaltyTransactionDto.from(loyaltyTransactionService.update(principal, loyaltyTransactionId, request)), "Loyalty transaction updated");
    }

    @DeleteMapping("/{loyaltyTransactionId}")
    @PreAuthorize("hasAnyAuthority('LOYALTY_TRANSACTION:DELETE:OWN','LOYALTY_TRANSACTION:DELETE:TEAM','LOYALTY_TRANSACTION:DELETE:DEPARTMENT','LOYALTY_TRANSACTION:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID loyaltyTransactionId, @AuthenticationPrincipal UserPrincipal principal) {
        loyaltyTransactionService.delete(principal, loyaltyTransactionId);
        return ApiResponse.ok(null, "Loyalty transaction deleted");
    }
}
