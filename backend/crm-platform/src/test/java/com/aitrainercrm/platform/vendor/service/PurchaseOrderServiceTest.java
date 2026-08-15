package com.aitrainercrm.platform.vendor.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.user.repository.UserRepository;
import com.aitrainercrm.platform.vendor.entity.PurchaseOrder;
import com.aitrainercrm.platform.vendor.repository.PurchaseOrderRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.mockito.Mockito.when;

/** See {@link PurchaseOrderService}'s javadoc for the clock-in/out-style stamp-once behavior this mostly exists to cover (mirrors {@code ShiftServiceTest}). */
@ExtendWith(MockitoExtension.class)
class PurchaseOrderServiceTest {

    @Mock private PurchaseOrderRepository purchaseOrderRepository;
    @Mock private VendorService vendorService;
    @Mock private UserRepository userRepository;
    @Mock private ScopeAuthorizationService scopeAuthorizationService;
    @Mock private ApplicationEventPublisher events;

    private PurchaseOrderService service;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private final UUID vendorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PurchaseOrderService(purchaseOrderRepository, vendorService, userRepository, scopeAuthorizationService, events);
    }

    private UserPrincipal principal() {
        return new UserPrincipal(callerId, "staff@example.com", organizationId, List.of());
    }

    @Test
    void updateStatus_movingToReceivedThenBackAndForth_stampsReceivedAtOnlyOnce() {
        UUID orderId = UUID.randomUUID();
        PurchaseOrder order = new PurchaseOrder(organizationId, vendorId, callerId, LocalDate.of(2026, 2, 1));
        order.setId(orderId);
        when(purchaseOrderRepository.findActiveByIdAndOrganizationId(orderId, organizationId)).thenReturn(Optional.of(order));

        PurchaseOrder received = service.updateStatus(principal(), orderId, PurchaseOrder.Status.RECEIVED);
        Instant receivedAt = received.getReceivedAt();
        assertThat(receivedAt).isNotNull();

        // A correction back to ORDERED, then re-entering RECEIVED, must not move receivedAt.
        PurchaseOrder backToOrdered = service.updateStatus(principal(), orderId, PurchaseOrder.Status.ORDERED);
        assertThat(backToOrdered.getReceivedAt()).isEqualTo(receivedAt);

        PurchaseOrder receivedAgain = service.updateStatus(principal(), orderId, PurchaseOrder.Status.RECEIVED);
        assertThat(receivedAgain.getReceivedAt()).isEqualTo(receivedAt);
    }
}
