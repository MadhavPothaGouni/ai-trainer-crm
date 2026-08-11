package com.aitrainercrm.platform.invoice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Body for {@code POST /invoices/from-order/{orderId}} - the order id comes from the path. issueDate/dueDate default to today / today+30 days when omitted (see InvoiceService#generateFromOrder). */
public record GenerateInvoiceRequest(
        @NotBlank @Size(max = 50) String invoiceNumber, LocalDate issueDate, LocalDate dueDate) {
}
