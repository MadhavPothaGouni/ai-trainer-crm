package com.aitrainercrm.platform.quote.service;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.opportunity.repository.OpportunityRepository;
import com.aitrainercrm.platform.product.repository.ProductRepository;
import com.aitrainercrm.platform.quote.dto.CreateQuoteLineItemRequest;
import com.aitrainercrm.platform.quote.dto.CreateQuoteRequest;
import com.aitrainercrm.platform.quote.dto.UpdateQuoteLineItemRequest;
import com.aitrainercrm.platform.quote.dto.UpdateQuoteRequest;
import com.aitrainercrm.platform.quote.entity.Quote;
import com.aitrainercrm.platform.quote.entity.QuoteLineItem;
import com.aitrainercrm.platform.quote.repository.QuoteLineItemRepository;
import com.aitrainercrm.platform.quote.repository.QuoteRepository;
import com.aitrainercrm.platform.role.entity.Permission;
import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quotes and their line items. Same ScopeAuthorizationService pattern as
 * AccountService for the quote header itself; line item mutations are
 * gated on QUOTE:UPDATE against the parent quote - there's no separate
 * line-item permission in the catalog, since adding/editing/removing a line
 * is just a way of updating the quote.
 */
@Service
@RequiredArgsConstructor
public class QuoteService {

    private static final Permission.Resource RESOURCE = Permission.Resource.QUOTE;

    private final QuoteRepository quoteRepository;
    private final QuoteLineItemRepository lineItemRepository;
    private final UserRepository userRepository;
    private final OpportunityRepository opportunityRepository;
    private final ProductRepository productRepository;
    private final ScopeAuthorizationService scopeAuthorizationService;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<Quote> list(UserPrincipal principal, UUID opportunityId, Pageable pageable) {
        Optional<Set<UUID>> visibleOwnerIds = scopeAuthorizationService.visibleOwnerIds(principal, RESOURCE, Permission.Action.READ);
        UUID organizationId = principal.getOrganizationId();

        if (opportunityId != null) {
            return visibleOwnerIds
                    .map(ownerIds -> quoteRepository.findByOrganizationIdAndOwnerIdInAndOpportunityIdAndDeletedAtIsNull(
                            organizationId, ownerIds, opportunityId, pageable))
                    .orElseGet(() -> quoteRepository.findByOrganizationIdAndOpportunityIdAndDeletedAtIsNull(organizationId, opportunityId, pageable));
        }

        return visibleOwnerIds
                .map(ownerIds -> quoteRepository.findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(organizationId, ownerIds, pageable))
                .orElseGet(() -> quoteRepository.findByOrganizationIdAndDeletedAtIsNull(organizationId, pageable));
    }

    @Transactional(readOnly = true)
    public Quote get(UserPrincipal principal, UUID quoteId) {
        Quote quote = findOrThrow(principal.getOrganizationId(), quoteId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.READ, quote.getOwnerId());
        return quote;
    }

    @Transactional(readOnly = true)
    public List<QuoteLineItem> getLineItems(UserPrincipal principal, UUID quoteId) {
        get(principal, quoteId); // re-validates existence + READ access
        return lineItemRepository.findByQuoteIdOrderByCreatedAtAsc(quoteId);
    }

    @Transactional
    public Quote create(UserPrincipal principal, CreateQuoteRequest request) {
        UUID ownerId = resolveOwner(principal, Permission.Action.CREATE, request.ownerId());
        assertOpportunityInOrganization(principal.getOrganizationId(), request.opportunityId());

        Quote quote = new Quote(principal.getOrganizationId(), request.opportunityId(), request.name(), ownerId);
        quote.setCurrency(request.currency());
        quote.setValidUntil(request.validUntil());
        quote.setDiscountAmount(nullToZero(request.discountAmount()));
        quote.setTaxAmount(nullToZero(request.taxAmount()));
        recomputeTotals(quote, List.of());
        quoteRepository.save(quote);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "Quote", quote.getId()));
        return quote;
    }

    @Transactional
    public Quote update(UserPrincipal principal, UUID quoteId, UpdateQuoteRequest request) {
        Quote quote = findOrThrow(principal.getOrganizationId(), quoteId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, quote.getOwnerId());

        quote.setName(request.name());
        quote.setCurrency(request.currency());
        quote.setValidUntil(request.validUntil());
        quote.setDiscountAmount(nullToZero(request.discountAmount()));
        quote.setTaxAmount(nullToZero(request.taxAmount()));
        recomputeTotals(quote, lineItemRepository.findByQuoteIdOrderByCreatedAtAsc(quoteId));
        quoteRepository.save(quote);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "Quote", quote.getId()));
        return quote;
    }

    @Transactional
    public Quote updateStatus(UserPrincipal principal, UUID quoteId, Quote.Status status) {
        Quote quote = findOrThrow(principal.getOrganizationId(), quoteId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, quote.getOwnerId());

        quote.setStatus(status);
        quoteRepository.save(quote);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "Quote", quote.getId()));
        return quote;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID quoteId) {
        Quote quote = findOrThrow(principal.getOrganizationId(), quoteId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.DELETE, quote.getOwnerId());

        quote.setDeletedAt(Instant.now());
        quoteRepository.save(quote);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "Quote", quoteId));
    }

    @Transactional
    public Quote assignOwner(UserPrincipal principal, UUID quoteId, UUID newOwnerId) {
        Quote quote = findOrThrow(principal.getOrganizationId(), quoteId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.ASSIGN, quote.getOwnerId());
        assertUserInOrganization(principal.getOrganizationId(), newOwnerId);

        quote.setOwnerId(newOwnerId);
        quoteRepository.save(quote);

        events.publishEvent(new CrmAuditEvents.RecordAssigned(principal.getId(), principal.getOrganizationId(), "Quote", quote.getId(), newOwnerId));
        return quote;
    }

    @Transactional
    public QuoteLineItem addLineItem(UserPrincipal principal, UUID quoteId, CreateQuoteLineItemRequest request) {
        Quote quote = findOrThrow(principal.getOrganizationId(), quoteId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, quote.getOwnerId());
        if (request.productId() != null) {
            assertProductInOrganization(principal.getOrganizationId(), request.productId());
        }

        QuoteLineItem lineItem = new QuoteLineItem(quoteId, request.description(), request.quantity(), request.unitPrice());
        lineItem.setProductId(request.productId());
        lineItemRepository.save(lineItem);

        recomputeTotals(quote, lineItemRepository.findByQuoteIdOrderByCreatedAtAsc(quoteId));
        quoteRepository.save(quote);
        return lineItem;
    }

    @Transactional
    public QuoteLineItem updateLineItem(UserPrincipal principal, UUID quoteId, UUID lineItemId, UpdateQuoteLineItemRequest request) {
        Quote quote = findOrThrow(principal.getOrganizationId(), quoteId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, quote.getOwnerId());
        if (request.productId() != null) {
            assertProductInOrganization(principal.getOrganizationId(), request.productId());
        }
        QuoteLineItem lineItem = lineItemRepository.findByIdAndQuoteId(lineItemId, quoteId)
                .orElseThrow(() -> new ResourceNotFoundException("QuoteLineItem", lineItemId));

        lineItem.setProductId(request.productId());
        lineItem.setDescription(request.description());
        lineItem.setQuantity(request.quantity());
        lineItem.setUnitPrice(request.unitPrice());
        lineItem.recomputeLineTotal();
        lineItemRepository.save(lineItem);

        recomputeTotals(quote, lineItemRepository.findByQuoteIdOrderByCreatedAtAsc(quoteId));
        quoteRepository.save(quote);
        return lineItem;
    }

    @Transactional
    public void removeLineItem(UserPrincipal principal, UUID quoteId, UUID lineItemId) {
        Quote quote = findOrThrow(principal.getOrganizationId(), quoteId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, quote.getOwnerId());
        QuoteLineItem lineItem = lineItemRepository.findByIdAndQuoteId(lineItemId, quoteId)
                .orElseThrow(() -> new ResourceNotFoundException("QuoteLineItem", lineItemId));

        lineItemRepository.delete(lineItem);

        recomputeTotals(quote, lineItemRepository.findByQuoteIdOrderByCreatedAtAsc(quoteId));
        quoteRepository.save(quote);
    }

    private Quote findOrThrow(UUID organizationId, UUID quoteId) {
        return quoteRepository.findActiveByIdAndOrganizationId(quoteId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Quote", quoteId));
    }

    /** subtotal = sum of every line's lineTotal; totalAmount = subtotal - discount + tax. Called after every line item add/edit/remove and on header update (discount/tax can change independently). */
    private void recomputeTotals(Quote quote, List<QuoteLineItem> lineItems) {
        BigDecimal subtotal = lineItems.stream().map(QuoteLineItem::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        quote.setSubtotal(subtotal);
        quote.setTotalAmount(subtotal.subtract(quote.getDiscountAmount()).add(quote.getTaxAmount()));
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private UUID resolveOwner(UserPrincipal principal, Permission.Action action, UUID requestedOwnerId) {
        if (requestedOwnerId == null || requestedOwnerId.equals(principal.getId())) {
            return principal.getId();
        }
        if (scopeAuthorizationService.highestGranted(principal, RESOURCE, action) != ScopeAuthorizationService.Access.ORGANIZATION) {
            throw new ForbiddenException("You can only " + action.name().toLowerCase(Locale.ROOT) + " records assigned to yourself");
        }
        assertUserInOrganization(principal.getOrganizationId(), requestedOwnerId);
        return requestedOwnerId;
    }

    private void assertUserInOrganization(UUID organizationId, UUID userId) {
        boolean exists = userRepository.findActiveById(userId)
                .map(u -> organizationId.equals(u.getOrganizationId()))
                .orElse(false);
        if (!exists) {
            throw new ResourceNotFoundException("User", userId);
        }
    }

    private void assertOpportunityInOrganization(UUID organizationId, UUID opportunityId) {
        if (!opportunityRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(opportunityId, organizationId)) {
            throw new ResourceNotFoundException("Opportunity", opportunityId);
        }
    }

    private void assertProductInOrganization(UUID organizationId, UUID productId) {
        if (!productRepository.existsByIdAndOrganizationIdAndDeletedAtIsNull(productId, organizationId)) {
            throw new ResourceNotFoundException("Product", productId);
        }
    }
}
