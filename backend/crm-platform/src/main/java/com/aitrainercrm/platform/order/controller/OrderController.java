package com.aitrainercrm.platform.order.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.order.dto.CreateOrderFromQuoteRequest;
import com.aitrainercrm.platform.order.dto.CreateOrderLineItemRequest;
import com.aitrainercrm.platform.order.dto.CreateOrderRequest;
import com.aitrainercrm.platform.order.dto.OrderDto;
import com.aitrainercrm.platform.order.dto.OrderLineItemDto;
import com.aitrainercrm.platform.order.dto.UpdateOrderLineItemRequest;
import com.aitrainercrm.platform.order.dto.UpdateOrderRequest;
import com.aitrainercrm.platform.order.dto.UpdateOrderStatusRequest;
import com.aitrainercrm.platform.order.entity.Order;
import com.aitrainercrm.platform.order.service.OrderService;
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

/** No OWN scope on ORDER (see OrderService's javadoc) - every @PreAuthorize here only lists TEAM/DEPARTMENT/ORGANIZATION, plus ORDER:APPROVE on /confirm. */
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ORDER:READ:TEAM','ORDER:READ:DEPARTMENT','ORDER:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<OrderDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<Order> page = orderService.list(principal, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(OrderDto::from).toList()));
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("hasAnyAuthority('ORDER:READ:TEAM','ORDER:READ:DEPARTMENT','ORDER:READ:ORGANIZATION')")
    public ApiResponse<OrderDto> get(@PathVariable UUID orderId, @AuthenticationPrincipal UserPrincipal principal) {
        Order order = orderService.get(principal, orderId);
        return ApiResponse.ok(OrderDto.from(order, orderService.getLineItems(principal, orderId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('ORDER:CREATE:TEAM','ORDER:CREATE:DEPARTMENT','ORDER:CREATE:ORGANIZATION')")
    public ApiResponse<OrderDto> create(@Valid @RequestBody CreateOrderRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(OrderDto.from(orderService.create(principal, request)), "Order created");
    }

    @PostMapping("/from-quote/{quoteId}")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('ORDER:CREATE:TEAM','ORDER:CREATE:DEPARTMENT','ORDER:CREATE:ORGANIZATION')")
    public ApiResponse<OrderDto> createFromQuote(
            @PathVariable UUID quoteId, @Valid @RequestBody CreateOrderFromQuoteRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        Order order = orderService.createFromQuote(principal, quoteId, request.orderNumber());
        return ApiResponse.ok(OrderDto.from(order, orderService.getLineItems(principal, order.getId())), "Order created from quote");
    }

    @PutMapping("/{orderId}")
    @PreAuthorize("hasAnyAuthority('ORDER:UPDATE:TEAM','ORDER:UPDATE:DEPARTMENT','ORDER:UPDATE:ORGANIZATION')")
    public ApiResponse<OrderDto> update(
            @PathVariable UUID orderId, @Valid @RequestBody UpdateOrderRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        Order order = orderService.update(principal, orderId, request);
        return ApiResponse.ok(OrderDto.from(order, orderService.getLineItems(principal, orderId)), "Order updated");
    }

    @PostMapping("/{orderId}/confirm")
    @PreAuthorize("hasAnyAuthority('ORDER:APPROVE:TEAM','ORDER:APPROVE:DEPARTMENT','ORDER:APPROVE:ORGANIZATION')")
    public ApiResponse<OrderDto> confirm(@PathVariable UUID orderId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(OrderDto.from(orderService.confirm(principal, orderId)), "Order confirmed");
    }

    @PatchMapping("/{orderId}/status")
    @PreAuthorize("hasAnyAuthority('ORDER:UPDATE:TEAM','ORDER:UPDATE:DEPARTMENT','ORDER:UPDATE:ORGANIZATION')")
    public ApiResponse<OrderDto> updateStatus(
            @PathVariable UUID orderId, @Valid @RequestBody UpdateOrderStatusRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(OrderDto.from(orderService.updateStatus(principal, orderId, request.status())), "Status updated");
    }

    @DeleteMapping("/{orderId}")
    @PreAuthorize("hasAnyAuthority('ORDER:DELETE:TEAM','ORDER:DELETE:DEPARTMENT','ORDER:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID orderId, @AuthenticationPrincipal UserPrincipal principal) {
        orderService.delete(principal, orderId);
        return ApiResponse.ok(null, "Order deleted");
    }

    @PostMapping("/{orderId}/line-items")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('ORDER:UPDATE:TEAM','ORDER:UPDATE:DEPARTMENT','ORDER:UPDATE:ORGANIZATION')")
    public ApiResponse<OrderLineItemDto> addLineItem(
            @PathVariable UUID orderId, @Valid @RequestBody CreateOrderLineItemRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(OrderLineItemDto.from(orderService.addLineItem(principal, orderId, request)), "Line item added");
    }

    @PutMapping("/{orderId}/line-items/{lineItemId}")
    @PreAuthorize("hasAnyAuthority('ORDER:UPDATE:TEAM','ORDER:UPDATE:DEPARTMENT','ORDER:UPDATE:ORGANIZATION')")
    public ApiResponse<OrderLineItemDto> updateLineItem(
            @PathVariable UUID orderId, @PathVariable UUID lineItemId,
            @Valid @RequestBody UpdateOrderLineItemRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(OrderLineItemDto.from(orderService.updateLineItem(principal, orderId, lineItemId, request)), "Line item updated");
    }

    @DeleteMapping("/{orderId}/line-items/{lineItemId}")
    @PreAuthorize("hasAnyAuthority('ORDER:UPDATE:TEAM','ORDER:UPDATE:DEPARTMENT','ORDER:UPDATE:ORGANIZATION')")
    public ApiResponse<Void> removeLineItem(
            @PathVariable UUID orderId, @PathVariable UUID lineItemId, @AuthenticationPrincipal UserPrincipal principal) {
        orderService.removeLineItem(principal, orderId, lineItemId);
        return ApiResponse.ok(null, "Line item removed");
    }
}
