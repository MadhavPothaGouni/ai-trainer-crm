package com.aitrainercrm.platform.payment.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.payment.dto.CreatePaymentRequest;
import com.aitrainercrm.platform.payment.dto.PaymentDto;
import com.aitrainercrm.platform.payment.entity.Payment;
import com.aitrainercrm.platform.payment.service.PaymentService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * No OWN scope on PAYMENT (see PaymentService's javadoc) - every
 * @PreAuthorize here only lists TEAM/DEPARTMENT/ORGANIZATION. No class-level
 * @RequestMapping, deliberately: recording/listing payments is naturally
 * nested under the invoice they belong to ({@code /invoices/{invoiceId}/payments}),
 * while a single payment is addressed directly by its own id ({@code
 * /payments/{paymentId}}) once you already have it - same "nested for
 * creation, flat for direct access" split QuoteController uses for line
 * items vs. AccountController uses for accounts themselves.
 */
@RestController
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping("/api/v1/invoices/{invoiceId}/payments")
    @PreAuthorize("hasAnyAuthority('PAYMENT:READ:TEAM','PAYMENT:READ:DEPARTMENT','PAYMENT:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<PaymentDto>> list(
            @PathVariable UUID invoiceId, Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<Payment> page = paymentService.list(principal, invoiceId, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(PaymentDto::from).toList()));
    }

    @PostMapping("/api/v1/invoices/{invoiceId}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('PAYMENT:CREATE:TEAM','PAYMENT:CREATE:DEPARTMENT','PAYMENT:CREATE:ORGANIZATION')")
    public ApiResponse<PaymentDto> record(
            @PathVariable UUID invoiceId, @Valid @RequestBody CreatePaymentRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(PaymentDto.from(paymentService.record(principal, invoiceId, request)), "Payment recorded");
    }

    @GetMapping("/api/v1/payments/{paymentId}")
    @PreAuthorize("hasAnyAuthority('PAYMENT:READ:TEAM','PAYMENT:READ:DEPARTMENT','PAYMENT:READ:ORGANIZATION')")
    public ApiResponse<PaymentDto> get(@PathVariable UUID paymentId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(PaymentDto.from(paymentService.get(principal, paymentId)));
    }

    @DeleteMapping("/api/v1/payments/{paymentId}")
    @PreAuthorize("hasAnyAuthority('PAYMENT:DELETE:TEAM','PAYMENT:DELETE:DEPARTMENT','PAYMENT:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID paymentId, @AuthenticationPrincipal UserPrincipal principal) {
        paymentService.delete(principal, paymentId);
        return ApiResponse.ok(null, "Payment deleted");
    }
}
