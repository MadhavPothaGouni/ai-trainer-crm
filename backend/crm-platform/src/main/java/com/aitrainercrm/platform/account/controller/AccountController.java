package com.aitrainercrm.platform.account.controller;

import com.aitrainercrm.platform.account.dto.AccountDto;
import com.aitrainercrm.platform.account.dto.CreateAccountRequest;
import com.aitrainercrm.platform.account.dto.UpdateAccountRequest;
import com.aitrainercrm.platform.account.entity.Account;
import com.aitrainercrm.platform.account.service.AccountService;
import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.AssignOwnerRequest;
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

/**
 * Every {@code @PreAuthorize} here only checks the caller holds *some*
 * level of the permission (any of OWN/TEAM/DEPARTMENT/ORGANIZATION) - it's
 * a coarse gate keeping out users with none at all. The actual per-record
 * decision (does this OWN-scope caller own *this* account?) happens inside
 * AccountService via ScopeAuthorizationService, because only the service
 * layer has the record loaded to check.
 */
@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ACCOUNT:READ:OWN','ACCOUNT:READ:TEAM','ACCOUNT:READ:DEPARTMENT','ACCOUNT:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<AccountDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<Account> page = accountService.list(principal, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(AccountDto::from).toList()));
    }

    @GetMapping("/{accountId}")
    @PreAuthorize("hasAnyAuthority('ACCOUNT:READ:OWN','ACCOUNT:READ:TEAM','ACCOUNT:READ:DEPARTMENT','ACCOUNT:READ:ORGANIZATION')")
    public ApiResponse<AccountDto> get(@PathVariable UUID accountId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(AccountDto.from(accountService.get(principal, accountId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('ACCOUNT:CREATE:OWN','ACCOUNT:CREATE:TEAM','ACCOUNT:CREATE:DEPARTMENT','ACCOUNT:CREATE:ORGANIZATION')")
    public ApiResponse<AccountDto> create(@Valid @RequestBody CreateAccountRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(AccountDto.from(accountService.create(principal, request)), "Account created");
    }

    @PutMapping("/{accountId}")
    @PreAuthorize("hasAnyAuthority('ACCOUNT:UPDATE:OWN','ACCOUNT:UPDATE:TEAM','ACCOUNT:UPDATE:DEPARTMENT','ACCOUNT:UPDATE:ORGANIZATION')")
    public ApiResponse<AccountDto> update(
            @PathVariable UUID accountId, @Valid @RequestBody UpdateAccountRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(AccountDto.from(accountService.update(principal, accountId, request)), "Account updated");
    }

    @DeleteMapping("/{accountId}")
    @PreAuthorize("hasAnyAuthority('ACCOUNT:DELETE:OWN','ACCOUNT:DELETE:TEAM','ACCOUNT:DELETE:DEPARTMENT','ACCOUNT:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID accountId, @AuthenticationPrincipal UserPrincipal principal) {
        accountService.delete(principal, accountId);
        return ApiResponse.ok(null, "Account deleted");
    }

    @PatchMapping("/{accountId}/owner")
    @PreAuthorize("hasAnyAuthority('ACCOUNT:ASSIGN:OWN','ACCOUNT:ASSIGN:TEAM','ACCOUNT:ASSIGN:DEPARTMENT','ACCOUNT:ASSIGN:ORGANIZATION')")
    public ApiResponse<AccountDto> assignOwner(
            @PathVariable UUID accountId, @Valid @RequestBody AssignOwnerRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(AccountDto.from(accountService.assignOwner(principal, accountId, request.ownerId())), "Owner updated");
    }
}
