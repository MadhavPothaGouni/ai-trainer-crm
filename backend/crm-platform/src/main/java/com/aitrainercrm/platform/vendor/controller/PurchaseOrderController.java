package com.aitrainercrm.platform.vendor.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.vendor.dto.CreatePurchaseOrderRequest;
import com.aitrainercrm.platform.vendor.dto.PurchaseOrderDto;
import com.aitrainercrm.platform.vendor.dto.UpdatePurchaseOrderRequest;
import com.aitrainercrm.platform.vendor.dto.UpdatePurchaseOrderStatusRequest;
import com.aitrainercrm.platform.vendor.entity.PurchaseOrder;
import com.aitrainercrm.platform.vendor.service.PurchaseOrderService;
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

/** Mirrors ShiftController's shape exactly, including the separate PATCH .../status endpoint for receivedAt stamping. */
@RestController
@RequestMapping("/api/v1/purchase-orders")
@RequiredArgsConstructor
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('PURCHASE_ORDER:READ:OWN','PURCHASE_ORDER:READ:TEAM','PURCHASE_ORDER:READ:DEPARTMENT','PURCHASE_ORDER:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<PurchaseOrderDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<PurchaseOrder> page = purchaseOrderService.list(principal, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(PurchaseOrderDto::from).toList()));
    }

    @GetMapping("/{purchaseOrderId}")
    @PreAuthorize("hasAnyAuthority('PURCHASE_ORDER:READ:OWN','PURCHASE_ORDER:READ:TEAM','PURCHASE_ORDER:READ:DEPARTMENT','PURCHASE_ORDER:READ:ORGANIZATION')")
    public ApiResponse<PurchaseOrderDto> get(@PathVariable UUID purchaseOrderId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(PurchaseOrderDto.from(purchaseOrderService.get(principal, purchaseOrderId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('PURCHASE_ORDER:CREATE:OWN','PURCHASE_ORDER:CREATE:TEAM','PURCHASE_ORDER:CREATE:DEPARTMENT','PURCHASE_ORDER:CREATE:ORGANIZATION')")
    public ApiResponse<PurchaseOrderDto> create(
            @Valid @RequestBody CreatePurchaseOrderRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(PurchaseOrderDto.from(purchaseOrderService.create(principal, request)), "Purchase order created");
    }

    @PutMapping("/{purchaseOrderId}")
    @PreAuthorize("hasAnyAuthority('PURCHASE_ORDER:UPDATE:OWN','PURCHASE_ORDER:UPDATE:TEAM','PURCHASE_ORDER:UPDATE:DEPARTMENT','PURCHASE_ORDER:UPDATE:ORGANIZATION')")
    public ApiResponse<PurchaseOrderDto> update(
            @PathVariable UUID purchaseOrderId, @Valid @RequestBody UpdatePurchaseOrderRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(PurchaseOrderDto.from(purchaseOrderService.update(principal, purchaseOrderId, request)), "Purchase order updated");
    }

    @PatchMapping("/{purchaseOrderId}/status")
    @PreAuthorize("hasAnyAuthority('PURCHASE_ORDER:UPDATE:OWN','PURCHASE_ORDER:UPDATE:TEAM','PURCHASE_ORDER:UPDATE:DEPARTMENT','PURCHASE_ORDER:UPDATE:ORGANIZATION')")
    public ApiResponse<PurchaseOrderDto> updateStatus(
            @PathVariable UUID purchaseOrderId,
            @Valid @RequestBody UpdatePurchaseOrderStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(PurchaseOrderDto.from(purchaseOrderService.updateStatus(principal, purchaseOrderId, request.status())), "Status updated");
    }

    @DeleteMapping("/{purchaseOrderId}")
    @PreAuthorize("hasAnyAuthority('PURCHASE_ORDER:DELETE:OWN','PURCHASE_ORDER:DELETE:TEAM','PURCHASE_ORDER:DELETE:DEPARTMENT','PURCHASE_ORDER:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID purchaseOrderId, @AuthenticationPrincipal UserPrincipal principal) {
        purchaseOrderService.delete(principal, purchaseOrderId);
        return ApiResponse.ok(null, "Purchase order deleted");
    }
}
