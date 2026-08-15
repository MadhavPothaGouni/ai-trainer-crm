package com.aitrainercrm.platform.compensation.entity;

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
 * One staff member's pay for one pay period - see V57's migration comment for the gap this fills.
 * Owner-scoped, full OWN/TEAM/DEPARTMENT/ORGANIZATION ladder. {@link #staffUserId} is who's being
 * paid, not the authorization subject - {@code ownerId} (whoever entered/manages the record) is
 * what {@link com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService} checks,
 * same split every {@code contactId}-vs-{@code ownerId} occurrence entity in this platform uses,
 * just against a {@code User} instead of a {@code Contact}. {@link #status} is a free (non-linear)
 * state machine - moving a record back to DRAFT after APPROVED is a legitimate correction, never
 * blocked. {@link #paidAt} is stamped once, the first time status moves to PAID.
 * {@link #totalAmount} is computed server-side by {@code CompensationRecordService}, never trusted
 * from the client.
 */
@Entity
@Table(name = "compensation_records")
@Getter
@Setter
@NoArgsConstructor
public class CompensationRecord extends BaseEntity {

    public enum Status {
        DRAFT, APPROVED, PAID
    }

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "staff_user_id", nullable = false)
    private UUID staffUserId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "pay_period_start", nullable = false)
    private LocalDate payPeriodStart;

    @Column(name = "pay_period_end", nullable = false)
    private LocalDate payPeriodEnd;

    @Column(name = "hours_worked", nullable = false, precision = 8, scale = 2)
    private BigDecimal hoursWorked = BigDecimal.ZERO;

    @Column(name = "hourly_rate", nullable = false, precision = 10, scale = 2)
    private BigDecimal hourlyRate = BigDecimal.ZERO;

    @Column(name = "commission_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal commissionAmount = BigDecimal.ZERO;

    @Column(name = "bonus_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal bonusAmount = BigDecimal.ZERO;

    /** hoursWorked * hourlyRate + commissionAmount + bonusAmount - computed server-side, see this class's javadoc. */
    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.DRAFT;

    /** Stamped once, on entering PAID - see this class's javadoc. */
    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(length = 2000)
    private String notes;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public CompensationRecord(
            UUID organizationId, UUID staffUserId, UUID ownerId, LocalDate payPeriodStart, LocalDate payPeriodEnd) {
        this.organizationId = organizationId;
        this.staffUserId = staffUserId;
        this.ownerId = ownerId;
        this.payPeriodStart = payPeriodStart;
        this.payPeriodEnd = payPeriodEnd;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
