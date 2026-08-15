package com.aitrainercrm.platform.vendor.entity;

import com.aitrainercrm.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One order placed with a {@link Vendor} - see V47's migration comment for the gap this fills.
 * Owner-scoped like {@link com.aitrainercrm.platform.equipment.entity.MaintenanceLog}, full
 * OWN/TEAM/DEPARTMENT/ORGANIZATION ladder. {@link #status} is a free (non-linear) state machine
 * like every other lifecycle field in this platform - moving a CANCELLED order back to ORDERED is
 * a legitimate correction, never blocked. {@link #receivedAt} is stamped the first time status
 * moves to RECEIVED and never overwritten afterward, same "stamp once" rule
 * {@code Shift#clockInAt}/{@code Contract#signedAt} already establish.
 */
@Entity
@Table(name = "purchase_orders")
@Getter
@Setter
@NoArgsConstructor
public class PurchaseOrder extends BaseEntity {

    public enum Status {
        DRAFT, ORDERED, RECEIVED, CANCELLED
    }

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "vendor_id", nullable = false)
    private UUID vendorId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.DRAFT;

    @Column(name = "total_amount", precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "expected_delivery_date")
    private LocalDate expectedDeliveryDate;

    /** Stamped once, on entering RECEIVED - see this class's javadoc. */
    @Column(name = "received_at")
    private Instant receivedAt;

    @Column(length = 2000)
    private String notes;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public PurchaseOrder(UUID organizationId, UUID vendorId, UUID ownerId, LocalDate orderDate) {
        this.organizationId = organizationId;
        this.vendorId = vendorId;
        this.ownerId = ownerId;
        this.orderDate = orderDate;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
