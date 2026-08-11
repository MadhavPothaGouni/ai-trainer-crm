package com.aitrainercrm.platform.invoice.service;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.common.exception.BusinessException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.invoice.dto.CreateInvoiceLineItemRequest;
import com.aitrainercrm.platform.invoice.dto.GenerateInvoiceRequest;
import com.aitrainercrm.platform.invoice.dto.UpdateInvoiceLineItemRequest;
import com.aitrainercrm.platform.invoice.dto.UpdateInvoiceRequest;
import com.aitrainercrm.platform.invoice.entity.Invoice;
import com.aitrainercrm.platform.invoice.entity.InvoiceLineItem;
import com.aitrainercrm.platform.invoice.repository.InvoiceLineItemRepository;
import com.aitrainercrm.platform.invoice.repository.InvoiceRepository;
import com.aitrainercrm.platform.order.entity.Order;
import com.aitrainercrm.platform.order.entity.OrderLineItem;
import com.aitrainercrm.platform.order.repository.OrderLineItemRepository;
import com.aitrainercrm.platform.order.repository.OrderRepository;
import com.aitrainercrm.platform.product.repository.ProductRepository;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Invoices and their line items. Same shared-org-resource pattern as
 * {@code OrderService}/{@code ProductService} - no
 * {@code ScopeAuthorizationService} calls; the controller's static
 * {@code @PreAuthorize} is the whole authorization story.
 */
@Service
@RequiredArgsConstructor
public class InvoiceService {

    private static final int DEFAULT_PAYMENT_TERMS_DAYS = 30;

    private final InvoiceRepository invoiceRepository;
    private final InvoiceLineItemRepository lineItemRepository;
    private final OrderRepository orderRepository;
    private final OrderLineItemRepository orderLineItemRepository;
    private final ProductRepository productRepository;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<Invoice> list(UserPrincipal principal, UUID orderId, Pageable pageable) {
        UUID organizationId = principal.getOrganizationId();
        if (orderId != null) {
            return invoiceRepository.findByOrganizationIdAndOrderIdAndDeletedAtIsNull(organizationId, orderId, pageable);
        }
        return invoiceRepository.findByOrganizationIdAndDeletedAtIsNull(organizationId, pageable);
    }

    @Transactional(readOnly = true)
    public Invoice get(UserPrincipal principal, UUID invoiceId) {
        return findOrThrow(principal.getOrganizationId(), invoiceId);
    }

    @Transactional(readOnly = true)
    public List<InvoiceLineItem> getLineItems(UserPrincipal principal, UUID invoiceId) {
        get(principal, invoiceId); // re-validates existence
        return lineItemRepository.findByInvoiceIdOrderByCreatedAtAsc(invoiceId);
    }

    /**
     * Generates a new DRAFT invoice from an order: clones every one of the
     * order's line items verbatim, same reasoning as
     * {@code OrderService#createFromQuote} cloning a quote's. {@code
     * issueDate} defaults to today and {@code dueDate} to 30 days out when
     * the caller doesn't specify them.
     */
    @Transactional
    public Invoice generateFromOrder(UserPrincipal principal, UUID orderId, GenerateInvoiceRequest request) {
        Order order = orderRepository
                .findActiveByIdAndOrganizationId(orderId, principal.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        LocalDate issueDate = request.issueDate() != null ? request.issueDate() : LocalDate.now();
        LocalDate dueDate = request.dueDate() != null ? request.dueDate() : issueDate.plusDays(DEFAULT_PAYMENT_TERMS_DAYS);

        Invoice invoice = new Invoice(principal.getOrganizationId(), order.getId(), request.invoiceNumber(), issueDate, dueDate);
        invoice.setCurrency(order.getCurrency());
        invoice.setDiscountAmount(order.getDiscountAmount());
        invoice.setTaxAmount(order.getTaxAmount());
        invoiceRepository.save(invoice);

        List<OrderLineItem> orderLineItems = orderLineItemRepository.findByOrderIdOrderByCreatedAtAsc(orderId);
        for (OrderLineItem orderLineItem : orderLineItems) {
            InvoiceLineItem lineItem = new InvoiceLineItem(
                    invoice.getId(), orderLineItem.getDescription(), orderLineItem.getQuantity(), orderLineItem.getUnitPrice());
            lineItem.setProductId(orderLineItem.getProductId());
            lineItemRepository.save(lineItem);
        }

        recomputeTotals(invoice, lineItemRepository.findByInvoiceIdOrderByCreatedAtAsc(invoice.getId()));
        invoiceRepository.save(invoice);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "Invoice", invoice.getId()));
        return invoice;
    }

    /** Only permitted while the invoice is still DRAFT - once SENT, its header is locked in (see #issue). */
    @Transactional
    public Invoice update(UserPrincipal principal, UUID invoiceId, UpdateInvoiceRequest request) {
        Invoice invoice = findOrThrow(principal.getOrganizationId(), invoiceId);
        assertDraft(invoice, "updated");

        invoice.setInvoiceNumber(request.invoiceNumber());
        invoice.setCurrency(request.currency());
        invoice.setIssueDate(request.issueDate());
        invoice.setDueDate(request.dueDate());
        invoice.setDiscountAmount(nullToZero(request.discountAmount()));
        invoice.setTaxAmount(nullToZero(request.taxAmount()));
        recomputeTotals(invoice, lineItemRepository.findByInvoiceIdOrderByCreatedAtAsc(invoiceId));
        invoiceRepository.save(invoice);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "Invoice", invoice.getId()));
        return invoice;
    }

    /** DRAFT -&gt; SENT only - the INVOICE:APPROVE-gated "sign off and send it" transition. See Invoice's javadoc. */
    @Transactional
    public Invoice issue(UserPrincipal principal, UUID invoiceId) {
        Invoice invoice = findOrThrow(principal.getOrganizationId(), invoiceId);
        if (invoice.getStatus() != Invoice.Status.DRAFT) {
            throw new BusinessException(
                    "INVOICE_INVALID_STATUS_TRANSITION", "Only a DRAFT invoice can be issued (was " + invoice.getStatus() + ")", HttpStatus.CONFLICT);
        }

        invoice.setStatus(Invoice.Status.SENT);
        invoiceRepository.save(invoice);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "Invoice", invoice.getId()));
        return invoice;
    }

    /** DRAFT or SENT (or OVERDUE) -&gt; VOID - never from PAID, an already-settled invoice can't be voided out from under its payments. */
    @Transactional
    public Invoice voidInvoice(UserPrincipal principal, UUID invoiceId) {
        Invoice invoice = findOrThrow(principal.getOrganizationId(), invoiceId);
        if (invoice.getStatus() == Invoice.Status.PAID) {
            throw new BusinessException("INVOICE_INVALID_STATUS_TRANSITION", "A PAID invoice cannot be voided", HttpStatus.CONFLICT);
        }
        if (invoice.getStatus() == Invoice.Status.VOID) {
            throw new BusinessException("INVOICE_INVALID_STATUS_TRANSITION", "This invoice is already VOID", HttpStatus.CONFLICT);
        }

        invoice.setStatus(Invoice.Status.VOID);
        invoiceRepository.save(invoice);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "Invoice", invoice.getId()));
        return invoice;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID invoiceId) {
        Invoice invoice = findOrThrow(principal.getOrganizationId(), invoiceId);

        invoice.setDeletedAt(Instant.now());
        invoiceRepository.save(invoice);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "Invoice", invoiceId));
    }

    @Transactional
    public InvoiceLineItem addLineItem(UserPrincipal principal, UUID invoiceId, CreateInvoiceLineItemRequest request) {
        Invoice invoice = findOrThrow(principal.getOrganizationId(), invoiceId);
        assertDraft(invoice, "have line items added");
        if (request.productId() != null) {
            assertProductInOrganization(principal.getOrganizationId(), request.productId());
        }

        InvoiceLineItem lineItem = new InvoiceLineItem(invoiceId, request.description(), request.quantity(), request.unitPrice());
        lineItem.setProductId(request.productId());
        lineItemRepository.save(lineItem);

        recomputeTotals(invoice, lineItemRepository.findByInvoiceIdOrderByCreatedAtAsc(invoiceId));
        invoiceRepository.save(invoice);
        return lineItem;
    }

    @Transactional
    public InvoiceLineItem updateLineItem(UserPrincipal principal, UUID invoiceId, UUID lineItemId, UpdateInvoiceLineItemRequest request) {
        Invoice invoice = findOrThrow(principal.getOrganizationId(), invoiceId);
        assertDraft(invoice, "have line items edited");
        if (request.productId() != null) {
            assertProductInOrganization(principal.getOrganizationId(), request.productId());
        }
        InvoiceLineItem lineItem = lineItemRepository.findByIdAndInvoiceId(lineItemId, invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("InvoiceLineItem", lineItemId));

        lineItem.setProductId(request.productId());
        lineItem.setDescription(request.description());
        lineItem.setQuantity(request.quantity());
        lineItem.setUnitPrice(request.unitPrice());
        lineItem.recomputeLineTotal();
        lineItemRepository.save(lineItem);

        recomputeTotals(invoice, lineItemRepository.findByInvoiceIdOrderByCreatedAtAsc(invoiceId));
        invoiceRepository.save(invoice);
        return lineItem;
    }

    @Transactional
    public void removeLineItem(UserPrincipal principal, UUID invoiceId, UUID lineItemId) {
        Invoice invoice = findOrThrow(principal.getOrganizationId(), invoiceId);
        assertDraft(invoice, "have line items removed");
        InvoiceLineItem lineItem = lineItemRepository.findByIdAndInvoiceId(lineItemId, invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("InvoiceLineItem", lineItemId));

        lineItemRepository.delete(lineItem);

        recomputeTotals(invoice, lineItemRepository.findByInvoiceIdOrderByCreatedAtAsc(invoiceId));
        invoiceRepository.save(invoice);
    }

    /**
     * Called by {@code PaymentService} every time a payment against this
     * invoice is recorded or removed - recomputes {@code amountPaid} from
     * the ledger PaymentService hands in and flips {@code status} to PAID
     * once it covers {@code totalAmount}, or back down to SENT if a payment
     * removal drops it below that again (never below SENT - a VOID invoice
     * stays VOID, a DRAFT invoice can't have payments against it at all per
     * {@code PaymentService#record}'s own DRAFT check).
     */
    @Transactional
    public void applyAmountPaid(UUID organizationId, UUID invoiceId, BigDecimal amountPaid) {
        Invoice invoice = findOrThrow(organizationId, invoiceId);
        invoice.setAmountPaid(amountPaid);
        if (invoice.getStatus() != Invoice.Status.VOID) {
            if (amountPaid.compareTo(invoice.getTotalAmount()) >= 0 && invoice.getTotalAmount().signum() > 0) {
                invoice.setStatus(Invoice.Status.PAID);
            } else if (invoice.getStatus() == Invoice.Status.PAID) {
                invoice.setStatus(Invoice.Status.SENT);
            }
        }
        invoiceRepository.save(invoice);
    }

    private Invoice findOrThrow(UUID organizationId, UUID invoiceId) {
        return invoiceRepository.findActiveByIdAndOrganizationId(invoiceId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", invoiceId));
    }

    private void assertDraft(Invoice invoice, String action) {
        if (invoice.getStatus() != Invoice.Status.DRAFT) {
            throw new BusinessException(
                    "INVOICE_NOT_DRAFT", "Only a DRAFT invoice can " + action + " (was " + invoice.getStatus() + ")", HttpStatus.CONFLICT);
        }
    }

    /** subtotal = sum of every line's lineTotal; totalAmount = subtotal - discount + tax. Same formula as OrderService/QuoteService#recomputeTotals. */
    private void recomputeTotals(Invoice invoice, List<InvoiceLineItem> lineItems) {
        BigDecimal subtotal = lineItems.stream().map(InvoiceLineItem::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        invoice.setSubtotal(subtotal);
        invoice.setTotalAmount(subtotal.subtract(invoice.getDiscountAmount()).add(invoice.getTaxAmount()));
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private void assertProductInOrganization(UUID organizationId, UUID productId) {
        if (!productRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(productId, organizationId)) {
            throw new ResourceNotFoundException("Product", productId);
        }
    }
}
