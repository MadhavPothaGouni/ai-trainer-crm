package com.aitrainercrm.platform.quote.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.AssignOwnerRequest;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.quote.dto.CreateQuoteLineItemRequest;
import com.aitrainercrm.platform.quote.dto.CreateQuoteRequest;
import com.aitrainercrm.platform.quote.dto.QuoteDto;
import com.aitrainercrm.platform.quote.dto.QuoteLineItemDto;
import com.aitrainercrm.platform.quote.dto.UpdateQuoteLineItemRequest;
import com.aitrainercrm.platform.quote.dto.UpdateQuoteRequest;
import com.aitrainercrm.platform.quote.dto.UpdateQuoteStatusRequest;
import com.aitrainercrm.platform.quote.entity.Quote;
import com.aitrainercrm.platform.quote.service.QuoteService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/quotes")
@RequiredArgsConstructor
public class QuoteController {

    private final QuoteService quoteService;

    /** opportunityId narrows to one deal's quotes (the common "quotes for this opportunity" view); omit it for a flat scope-filtered list. */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('QUOTE:READ:OWN','QUOTE:READ:TEAM','QUOTE:READ:DEPARTMENT','QUOTE:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<QuoteDto>> list(
            @RequestParam(required = false) UUID opportunityId, Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<Quote> page = quoteService.list(principal, opportunityId, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(QuoteDto::from).toList()));
    }

    @GetMapping("/{quoteId}")
    @PreAuthorize("hasAnyAuthority('QUOTE:READ:OWN','QUOTE:READ:TEAM','QUOTE:READ:DEPARTMENT','QUOTE:READ:ORGANIZATION')")
    public ApiResponse<QuoteDto> get(@PathVariable UUID quoteId, @AuthenticationPrincipal UserPrincipal principal) {
        Quote quote = quoteService.get(principal, quoteId);
        return ApiResponse.ok(QuoteDto.from(quote, quoteService.getLineItems(principal, quoteId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('QUOTE:CREATE:OWN','QUOTE:CREATE:TEAM','QUOTE:CREATE:DEPARTMENT','QUOTE:CREATE:ORGANIZATION')")
    public ApiResponse<QuoteDto> create(@Valid @RequestBody CreateQuoteRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(QuoteDto.from(quoteService.create(principal, request)), "Quote created");
    }

    @PutMapping("/{quoteId}")
    @PreAuthorize("hasAnyAuthority('QUOTE:UPDATE:OWN','QUOTE:UPDATE:TEAM','QUOTE:UPDATE:DEPARTMENT','QUOTE:UPDATE:ORGANIZATION')")
    public ApiResponse<QuoteDto> update(
            @PathVariable UUID quoteId, @Valid @RequestBody UpdateQuoteRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        Quote quote = quoteService.update(principal, quoteId, request);
        return ApiResponse.ok(QuoteDto.from(quote, quoteService.getLineItems(principal, quoteId)), "Quote updated");
    }

    @PatchMapping("/{quoteId}/status")
    @PreAuthorize("hasAnyAuthority('QUOTE:UPDATE:OWN','QUOTE:UPDATE:TEAM','QUOTE:UPDATE:DEPARTMENT','QUOTE:UPDATE:ORGANIZATION')")
    public ApiResponse<QuoteDto> updateStatus(
            @PathVariable UUID quoteId, @Valid @RequestBody UpdateQuoteStatusRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(QuoteDto.from(quoteService.updateStatus(principal, quoteId, request.status())), "Status updated");
    }

    @DeleteMapping("/{quoteId}")
    @PreAuthorize("hasAnyAuthority('QUOTE:DELETE:OWN','QUOTE:DELETE:TEAM','QUOTE:DELETE:DEPARTMENT','QUOTE:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID quoteId, @AuthenticationPrincipal UserPrincipal principal) {
        quoteService.delete(principal, quoteId);
        return ApiResponse.ok(null, "Quote deleted");
    }

    @PatchMapping("/{quoteId}/owner")
    @PreAuthorize("hasAnyAuthority('QUOTE:ASSIGN:OWN','QUOTE:ASSIGN:TEAM','QUOTE:ASSIGN:DEPARTMENT','QUOTE:ASSIGN:ORGANIZATION')")
    public ApiResponse<QuoteDto> assignOwner(
            @PathVariable UUID quoteId, @Valid @RequestBody AssignOwnerRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(QuoteDto.from(quoteService.assignOwner(principal, quoteId, request.ownerId())), "Owner updated");
    }

    @PostMapping("/{quoteId}/line-items")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('QUOTE:UPDATE:OWN','QUOTE:UPDATE:TEAM','QUOTE:UPDATE:DEPARTMENT','QUOTE:UPDATE:ORGANIZATION')")
    public ApiResponse<QuoteLineItemDto> addLineItem(
            @PathVariable UUID quoteId, @Valid @RequestBody CreateQuoteLineItemRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(QuoteLineItemDto.from(quoteService.addLineItem(principal, quoteId, request)), "Line item added");
    }

    @PutMapping("/{quoteId}/line-items/{lineItemId}")
    @PreAuthorize("hasAnyAuthority('QUOTE:UPDATE:OWN','QUOTE:UPDATE:TEAM','QUOTE:UPDATE:DEPARTMENT','QUOTE:UPDATE:ORGANIZATION')")
    public ApiResponse<QuoteLineItemDto> updateLineItem(
            @PathVariable UUID quoteId, @PathVariable UUID lineItemId,
            @Valid @RequestBody UpdateQuoteLineItemRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(QuoteLineItemDto.from(quoteService.updateLineItem(principal, quoteId, lineItemId, request)), "Line item updated");
    }

    @DeleteMapping("/{quoteId}/line-items/{lineItemId}")
    @PreAuthorize("hasAnyAuthority('QUOTE:UPDATE:OWN','QUOTE:UPDATE:TEAM','QUOTE:UPDATE:DEPARTMENT','QUOTE:UPDATE:ORGANIZATION')")
    public ApiResponse<Void> removeLineItem(
            @PathVariable UUID quoteId, @PathVariable UUID lineItemId, @AuthenticationPrincipal UserPrincipal principal) {
        quoteService.removeLineItem(principal, quoteId, lineItemId);
        return ApiResponse.ok(null, "Line item removed");
    }
}
