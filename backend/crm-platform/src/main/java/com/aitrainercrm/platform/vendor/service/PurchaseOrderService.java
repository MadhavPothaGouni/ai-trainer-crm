package com.aitrainercrm.platform.vendor.service;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.common.exception.ForbiddenException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.role.entity.Permission;
import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.user.repository.UserRepository;
import com.aitrainercrm.platform.vendor.dto.CreatePurchaseOrderRequest;
import com.aitrainercrm.platform.vendor.dto.UpdatePurchaseOrderRequest;
import com.aitrainercrm.platform.vendor.entity.PurchaseOrder;
import com.aitrainercrm.platform.vendor.repository.PurchaseOrderRepository;
import java.time.Instant;
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
 * Orders placed with a {@link com.aitrainercrm.platform.vendor.entity.Vendor} - see
 * {@link PurchaseOrder}'s javadoc and V47's migration comment for the backstory. Follows the
 * exact same shape as {@code ShiftService}: OWN/TEAM/DEPARTMENT/ORGANIZATION record-level
 * authorization via {@link ScopeAuthorizationService}, {@code resolveOwner} defaulting a null
 * {@code ownerId} to the caller, {@link #updateStatus} stamping {@code receivedAt} the first time
 * status moves to RECEIVED.
 */
@Service
@RequiredArgsConstructor
public class PurchaseOrderService {

    private static final Permission.Resource RESOURCE = Permission.Resource.PURCHASE_ORDER;

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final VendorService vendorService;
    private final UserRepository userRepository;
    private final ScopeAuthorizationService scopeAuthorizationService;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<PurchaseOrder> list(UserPrincipal principal, Pageable pageable) {
        Optional<Set<UUID>> visibleOwnerIds = scopeAuthorizationService.visibleOwnerIds(principal, RESOURCE, Permission.Action.READ);
        return visibleOwnerIds
                .map(ownerIds -> purchaseOrderRepository.findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(principal.getOrganizationId(), ownerIds, pageable))
                .orElseGet(() -> purchaseOrderRepository.findByOrganizationIdAndDeletedAtIsNull(principal.getOrganizationId(), pageable));
    }

    @Transactional(readOnly = true)
    public PurchaseOrder get(UserPrincipal principal, UUID purchaseOrderId) {
        PurchaseOrder order = findOrThrow(principal.getOrganizationId(), purchaseOrderId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.READ, order.getOwnerId());
        return order;
    }

    @Transactional
    public PurchaseOrder create(UserPrincipal principal, CreatePurchaseOrderRequest request) {
        UUID ownerId = resolveOwner(principal, Permission.Action.CREATE, request.ownerId());
        vendorService.findOrThrow(principal.getOrganizationId(), request.vendorId());

        PurchaseOrder order = new PurchaseOrder(principal.getOrganizationId(), request.vendorId(), ownerId, request.orderDate());
        order.setTotalAmount(request.totalAmount());
        order.setExpectedDeliveryDate(request.expectedDeliveryDate());
        order.setNotes(request.notes());
        purchaseOrderRepository.save(order);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "PurchaseOrder", order.getId()));
        return order;
    }

    @Transactional
    public PurchaseOrder update(UserPrincipal principal, UUID purchaseOrderId, UpdatePurchaseOrderRequest request) {
        PurchaseOrder order = findOrThrow(principal.getOrganizationId(), purchaseOrderId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, order.getOwnerId());

        order.setOrderDate(request.orderDate());
        order.setTotalAmount(request.totalAmount());
        order.setExpectedDeliveryDate(request.expectedDeliveryDate());
        order.setNotes(request.notes());
        purchaseOrderRepository.save(order);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "PurchaseOrder", order.getId()));
        return order;
    }

    /**
     * No invalid-transition checks, same restraint {@code ShiftService#updateStatus}'s javadoc
     * documents - moving a CANCELLED order back to ORDERED is a legitimate correction.
     * {@code receivedAt} is stamped the first time status moves to RECEIVED and never overwritten
     * afterward.
     */
    @Transactional
    public PurchaseOrder updateStatus(UserPrincipal principal, UUID purchaseOrderId, PurchaseOrder.Status newStatus) {
        PurchaseOrder order = findOrThrow(principal.getOrganizationId(), purchaseOrderId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.UPDATE, order.getOwnerId());

        if (newStatus == PurchaseOrder.Status.RECEIVED && order.getReceivedAt() == null) {
            order.setReceivedAt(Instant.now());
        }
        order.setStatus(newStatus);
        purchaseOrderRepository.save(order);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "PurchaseOrder", order.getId()));
        return order;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID purchaseOrderId) {
        PurchaseOrder order = findOrThrow(principal.getOrganizationId(), purchaseOrderId);
        scopeAuthorizationService.assertCanAccess(principal, RESOURCE, Permission.Action.DELETE, order.getOwnerId());

        order.setDeletedAt(Instant.now());
        purchaseOrderRepository.save(order);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "PurchaseOrder", purchaseOrderId));
    }

    private PurchaseOrder findOrThrow(UUID organizationId, UUID purchaseOrderId) {
        return purchaseOrderRepository.findActiveByIdAndOrganizationId(purchaseOrderId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("PurchaseOrder", purchaseOrderId));
    }

    private UUID resolveOwner(UserPrincipal principal, Permission.Action action, UUID requestedOwnerId) {
        if (requestedOwnerId == null || requestedOwnerId.equals(principal.getId())) {
            return principal.getId();
        }
        if (scopeAuthorizationService.highestGranted(principal, RESOURCE, action) != ScopeAuthorizationService.Access.ORGANIZATION) {
            throw new ForbiddenException("You can only " + action.name().toLowerCase(Locale.ROOT) + " purchase orders assigned to yourself");
        }
        assertUserInOrganization(principal.getOrganizationId(), requestedOwnerId);
        return requestedOwnerId;
    }

    private void assertUserInOrganization(UUID organizationId, UUID userId) {
        boolean exists = userRepository.findActiveById(userId).map(u -> organizationId.equals(u.getOrganizationId())).orElse(false);
        if (!exists) {
            throw new ResourceNotFoundException("User", userId);
        }
    }
}
