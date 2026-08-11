package com.aitrainercrm.platform.order.service;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.common.exception.BusinessException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.order.dto.CreateOrderLineItemRequest;
import com.aitrainercrm.platform.order.dto.CreateOrderRequest;
import com.aitrainercrm.platform.order.dto.UpdateOrderLineItemRequest;
import com.aitrainercrm.platform.order.dto.UpdateOrderRequest;
import com.aitrainercrm.platform.order.entity.Order;
import com.aitrainercrm.platform.order.entity.OrderLineItem;
import com.aitrainercrm.platform.order.repository.OrderLineItemRepository;
import com.aitrainercrm.platform.order.repository.OrderRepository;
import com.aitrainercrm.platform.product.repository.ProductRepository;
import com.aitrainercrm.platform.quote.entity.Quote;
import com.aitrainercrm.platform.quote.entity.QuoteLineItem;
import com.aitrainercrm.platform.quote.repository.QuoteLineItemRepository;
import com.aitrainercrm.platform.quote.repository.QuoteRepository;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import java.math.BigDecimal;
import java.time.Instant;
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
 * Orders and their line items. Same shared-org-resource pattern as
 * {@code ProductService} - no {@code ScopeAuthorizationService} calls here;
 * the controller's static {@code @PreAuthorize} (TEAM/DEPARTMENT/
 * ORGANIZATION only, no OWN - see V2's seeded ORDER permissions) is the
 * whole authorization story. Line item mutations are gated on
 * {@code ORDER:UPDATE} against the parent order, same reasoning as
 * {@code QuoteService}'s line items.
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderLineItemRepository lineItemRepository;
    private final QuoteRepository quoteRepository;
    private final QuoteLineItemRepository quoteLineItemRepository;
    private final ProductRepository productRepository;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<Order> list(UserPrincipal principal, Pageable pageable) {
        return orderRepository.findByOrganizationIdAndDeletedAtIsNull(principal.getOrganizationId(), pageable);
    }

    @Transactional(readOnly = true)
    public Order get(UserPrincipal principal, UUID orderId) {
        return findOrThrow(principal.getOrganizationId(), orderId);
    }

    @Transactional(readOnly = true)
    public List<OrderLineItem> getLineItems(UserPrincipal principal, UUID orderId) {
        get(principal, orderId); // re-validates existence
        return lineItemRepository.findByOrderIdOrderByCreatedAtAsc(orderId);
    }

    @Transactional
    public Order create(UserPrincipal principal, CreateOrderRequest request) {
        Order order = new Order(principal.getOrganizationId(), request.orderNumber());
        order.setCurrency(request.currency());
        order.setDiscountAmount(nullToZero(request.discountAmount()));
        order.setTaxAmount(nullToZero(request.taxAmount()));
        recomputeTotals(order, List.of());
        orderRepository.save(order);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "Order", order.getId()));
        return order;
    }

    /**
     * Converts a {@link Quote} into a new DRAFT order: clones every one of
     * the quote's line items verbatim (description/quantity/unit price, not
     * re-resolved from the product) and copies its currency/discount/tax, so
     * the order reflects exactly what was quoted. Only checks the quote
     * exists in this organization - it deliberately doesn't also require
     * QUOTE:READ on the specific quote, the same way QuoteService's own
     * assertProductInOrganization doesn't check PRODUCT:READ; creating an
     * order is an ORDER:CREATE action, and the controller already gates that.
     */
    @Transactional
    public Order createFromQuote(UserPrincipal principal, UUID quoteId, String orderNumber) {
        Quote quote = quoteRepository
                .findActiveByIdAndOrganizationId(quoteId, principal.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Quote", quoteId));

        Order order = new Order(principal.getOrganizationId(), orderNumber);
        order.setQuoteId(quote.getId());
        order.setCurrency(quote.getCurrency());
        order.setDiscountAmount(quote.getDiscountAmount());
        order.setTaxAmount(quote.getTaxAmount());
        orderRepository.save(order);

        List<QuoteLineItem> quoteLineItems = quoteLineItemRepository.findByQuoteIdOrderByCreatedAtAsc(quoteId);
        for (QuoteLineItem quoteLineItem : quoteLineItems) {
            OrderLineItem lineItem =
                    new OrderLineItem(order.getId(), quoteLineItem.getDescription(), quoteLineItem.getQuantity(), quoteLineItem.getUnitPrice());
            lineItem.setProductId(quoteLineItem.getProductId());
            lineItemRepository.save(lineItem);
        }

        recomputeTotals(order, lineItemRepository.findByOrderIdOrderByCreatedAtAsc(order.getId()));
        orderRepository.save(order);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "Order", order.getId()));
        return order;
    }

    @Transactional
    public Order update(UserPrincipal principal, UUID orderId, UpdateOrderRequest request) {
        Order order = findOrThrow(principal.getOrganizationId(), orderId);

        order.setOrderNumber(request.orderNumber());
        order.setCurrency(request.currency());
        order.setDiscountAmount(nullToZero(request.discountAmount()));
        order.setTaxAmount(nullToZero(request.taxAmount()));
        recomputeTotals(order, lineItemRepository.findByOrderIdOrderByCreatedAtAsc(orderId));
        orderRepository.save(order);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "Order", order.getId()));
        return order;
    }

    /** DRAFT -&gt; CONFIRMED only - the ORDER:APPROVE-gated "sign off" transition. See Order's javadoc. */
    @Transactional
    public Order confirm(UserPrincipal principal, UUID orderId) {
        Order order = findOrThrow(principal.getOrganizationId(), orderId);
        if (order.getStatus() != Order.Status.DRAFT) {
            throw new BusinessException(
                    "ORDER_INVALID_STATUS_TRANSITION", "Only a DRAFT order can be confirmed (was " + order.getStatus() + ")", HttpStatus.CONFLICT);
        }

        order.setStatus(Order.Status.CONFIRMED);
        orderRepository.save(order);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "Order", order.getId()));
        return order;
    }

    /** FULFILLED (from CONFIRMED only) or CANCELLED (from DRAFT or CONFIRMED) - the plain ORDER:UPDATE-gated transitions. */
    @Transactional
    public Order updateStatus(UserPrincipal principal, UUID orderId, Order.Status status) {
        Order order = findOrThrow(principal.getOrganizationId(), orderId);
        validateStatusTransition(order.getStatus(), status);

        order.setStatus(status);
        orderRepository.save(order);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "Order", order.getId()));
        return order;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID orderId) {
        Order order = findOrThrow(principal.getOrganizationId(), orderId);

        order.setDeletedAt(Instant.now());
        orderRepository.save(order);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "Order", orderId));
    }

    @Transactional
    public OrderLineItem addLineItem(UserPrincipal principal, UUID orderId, CreateOrderLineItemRequest request) {
        Order order = findOrThrow(principal.getOrganizationId(), orderId);
        if (request.productId() != null) {
            assertProductInOrganization(principal.getOrganizationId(), request.productId());
        }

        OrderLineItem lineItem = new OrderLineItem(orderId, request.description(), request.quantity(), request.unitPrice());
        lineItem.setProductId(request.productId());
        lineItemRepository.save(lineItem);

        recomputeTotals(order, lineItemRepository.findByOrderIdOrderByCreatedAtAsc(orderId));
        orderRepository.save(order);
        return lineItem;
    }

    @Transactional
    public OrderLineItem updateLineItem(UserPrincipal principal, UUID orderId, UUID lineItemId, UpdateOrderLineItemRequest request) {
        Order order = findOrThrow(principal.getOrganizationId(), orderId);
        if (request.productId() != null) {
            assertProductInOrganization(principal.getOrganizationId(), request.productId());
        }
        OrderLineItem lineItem = lineItemRepository.findByIdAndOrderId(lineItemId, orderId)
                .orElseThrow(() -> new ResourceNotFoundException("OrderLineItem", lineItemId));

        lineItem.setProductId(request.productId());
        lineItem.setDescription(request.description());
        lineItem.setQuantity(request.quantity());
        lineItem.setUnitPrice(request.unitPrice());
        lineItem.recomputeLineTotal();
        lineItemRepository.save(lineItem);

        recomputeTotals(order, lineItemRepository.findByOrderIdOrderByCreatedAtAsc(orderId));
        orderRepository.save(order);
        return lineItem;
    }

    @Transactional
    public void removeLineItem(UserPrincipal principal, UUID orderId, UUID lineItemId) {
        Order order = findOrThrow(principal.getOrganizationId(), orderId);
        OrderLineItem lineItem = lineItemRepository.findByIdAndOrderId(lineItemId, orderId)
                .orElseThrow(() -> new ResourceNotFoundException("OrderLineItem", lineItemId));

        lineItemRepository.delete(lineItem);

        recomputeTotals(order, lineItemRepository.findByOrderIdOrderByCreatedAtAsc(orderId));
        orderRepository.save(order);
    }

    private Order findOrThrow(UUID organizationId, UUID orderId) {
        return orderRepository.findActiveByIdAndOrganizationId(orderId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
    }

    private void validateStatusTransition(Order.Status from, Order.Status to) {
        boolean valid = switch (to) {
            case FULFILLED -> from == Order.Status.CONFIRMED;
            case CANCELLED -> from == Order.Status.DRAFT || from == Order.Status.CONFIRMED;
            case DRAFT, CONFIRMED -> false; // DRAFT->CONFIRMED only via #confirm; nothing moves back to DRAFT
        };
        if (!valid) {
            throw new BusinessException(
                    "ORDER_INVALID_STATUS_TRANSITION", "Cannot move an order from " + from + " to " + to, HttpStatus.CONFLICT);
        }
    }

    /** subtotal = sum of every line's lineTotal; totalAmount = subtotal - discount + tax. Same formula as QuoteService#recomputeTotals. */
    private void recomputeTotals(Order order, List<OrderLineItem> lineItems) {
        BigDecimal subtotal = lineItems.stream().map(OrderLineItem::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setSubtotal(subtotal);
        order.setTotalAmount(subtotal.subtract(order.getDiscountAmount()).add(order.getTaxAmount()));
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
