package com.aitrainercrm.platform.invoice.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.invoice.dto.CreateInvoiceLineItemRequest;
import com.aitrainercrm.platform.invoice.dto.GenerateInvoiceRequest;
import com.aitrainercrm.platform.invoice.dto.InvoiceDto;
import com.aitrainercrm.platform.invoice.dto.InvoiceLineItemDto;
import com.aitrainercrm.platform.invoice.dto.UpdateInvoiceLineItemRequest;
import com.aitrainercrm.platform.invoice.dto.UpdateInvoiceRequest;
import com.aitrainercrm.platform.invoice.entity.Invoice;
import com.aitrainercrm.platform.invoice.service.InvoiceService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** No OWN scope on INVOICE (see InvoiceService's javadoc) - every @PreAuthorize here only lists TEAM/DEPARTMENT/ORGANIZATION, plus INVOICE:APPROVE on /issue. There's no plain POST /invoices - every invoice comes from /invoices/from-order/{orderId}. */
@RestController
@RequestMapping("/api/v1/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;

    /** orderId narrows to one order's invoices; omit it for the flat org-wide list. */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('INVOICE:READ:TEAM','INVOICE:READ:DEPARTMENT','INVOICE:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<InvoiceDto>> list(
            @RequestParam(required = false) UUID orderId, Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<Invoice> page = invoiceService.list(principal, orderId, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(InvoiceDto::from).toList()));
    }

    @GetMapping("/{invoiceId}")
    @PreAuthorize("hasAnyAuthority('INVOICE:READ:TEAM','INVOICE:READ:DEPARTMENT','INVOICE:READ:ORGANIZATION')")
    public ApiResponse<InvoiceDto> get(@PathVariable UUID invoiceId, @AuthenticationPrincipal UserPrincipal principal) {
        Invoice invoice = invoiceService.get(principal, invoiceId);
        return ApiResponse.ok(InvoiceDto.from(invoice, invoiceService.getLineItems(principal, invoiceId)));
    }

    @PostMapping("/from-order/{orderId}")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('INVOICE:CREATE:TEAM','INVOICE:CREATE:DEPARTMENT','INVOICE:CREATE:ORGANIZATION')")
    public ApiResponse<InvoiceDto> generateFromOrder(
            @PathVariable UUID orderId, @Valid @RequestBody GenerateInvoiceRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        Invoice invoice = invoiceService.generateFromOrder(principal, orderId, request);
        return ApiResponse.ok(InvoiceDto.from(invoice, invoiceService.getLineItems(principal, invoice.getId())), "Invoice generated");
    }

    @PutMapping("/{invoiceId}")
    @PreAuthorize("hasAnyAuthority('INVOICE:UPDATE:TEAM','INVOICE:UPDATE:DEPARTMENT','INVOICE:UPDATE:ORGANIZATION')")
    public ApiResponse<InvoiceDto> update(
            @PathVariable UUID invoiceId, @Valid @RequestBody UpdateInvoiceRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        Invoice invoice = invoiceService.update(principal, invoiceId, request);
        return ApiResponse.ok(InvoiceDto.from(invoice, invoiceService.getLineItems(principal, invoiceId)), "Invoice updated");
    }

    @PostMapping("/{invoiceId}/issue")
    @PreAuthorize("hasAnyAuthority('INVOICE:APPROVE:TEAM','INVOICE:APPROVE:DEPARTMENT','INVOICE:APPROVE:ORGANIZATION')")
    public ApiResponse<InvoiceDto> issue(@PathVariable UUID invoiceId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(InvoiceDto.from(invoiceService.issue(principal, invoiceId)), "Invoice issued");
    }

    @PostMapping("/{invoiceId}/void")
    @PreAuthorize("hasAnyAuthority('INVOICE:UPDATE:TEAM','INVOICE:UPDATE:DEPARTMENT','INVOICE:UPDATE:ORGANIZATION')")
    public ApiResponse<InvoiceDto> voidInvoice(@PathVariable UUID invoiceId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(InvoiceDto.from(invoiceService.voidInvoice(principal, invoiceId)), "Invoice voided");
    }

    @DeleteMapping("/{invoiceId}")
    @PreAuthorize("hasAnyAuthority('INVOICE:DELETE:TEAM','INVOICE:DELETE:DEPARTMENT','INVOICE:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID invoiceId, @AuthenticationPrincipal UserPrincipal principal) {
        invoiceService.delete(principal, invoiceId);
        return ApiResponse.ok(null, "Invoice deleted");
    }

    @PostMapping("/{invoiceId}/line-items")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('INVOICE:UPDATE:TEAM','INVOICE:UPDATE:DEPARTMENT','INVOICE:UPDATE:ORGANIZATION')")
    public ApiResponse<InvoiceLineItemDto> addLineItem(
            @PathVariable UUID invoiceId, @Valid @RequestBody CreateInvoiceLineItemRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(InvoiceLineItemDto.from(invoiceService.addLineItem(principal, invoiceId, request)), "Line item added");
    }

    @PutMapping("/{invoiceId}/line-items/{lineItemId}")
    @PreAuthorize("hasAnyAuthority('INVOICE:UPDATE:TEAM','INVOICE:UPDATE:DEPARTMENT','INVOICE:UPDATE:ORGANIZATION')")
    public ApiResponse<InvoiceLineItemDto> updateLineItem(
            @PathVariable UUID invoiceId, @PathVariable UUID lineItemId,
            @Valid @RequestBody UpdateInvoiceLineItemRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(InvoiceLineItemDto.from(invoiceService.updateLineItem(principal, invoiceId, lineItemId, request)), "Line item updated");
    }

    @DeleteMapping("/{invoiceId}/line-items/{lineItemId}")
    @PreAuthorize("hasAnyAuthority('INVOICE:UPDATE:TEAM','INVOICE:UPDATE:DEPARTMENT','INVOICE:UPDATE:ORGANIZATION')")
    public ApiResponse<Void> removeLineItem(
            @PathVariable UUID invoiceId, @PathVariable UUID lineItemId, @AuthenticationPrincipal UserPrincipal principal) {
        invoiceService.removeLineItem(principal, invoiceId, lineItemId);
        return ApiResponse.ok(null, "Line item removed");
    }
}
