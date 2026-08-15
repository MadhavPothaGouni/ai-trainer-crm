package com.aitrainercrm.platform.checkin.entity;

import com.aitrainercrm.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A facility access log entry - see V52's migration comment for the gap this fills (distinct from
 * {@code ClassAttendance}/{@code TrainingSession}, which are tied to a specific activity). Owner-
 * scoped like {@link com.aitrainercrm.platform.timeoff.entity.TimeOffRequest}, full
 * OWN/TEAM/DEPARTMENT/ORGANIZATION ladder. {@code contactId} is the client who checked in, not the
 * authorization subject - {@code ownerId} (the staff member who logged it, or the front-desk
 * account for a kiosk/fob check-in) is what
 * {@link com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService} checks, same
 * split {@code ClientDocument#contactId}/{@code LockerAssignment#contactId} established. {@link
 * #status} is a free (non-linear) state machine - correcting a mistaken CHECKED_OUT back to
 * CHECKED_IN is a legitimate correction, never blocked. {@link #checkedInAt} is set once at
 * creation and never changes; {@link #checkedOutAt} follows the "stamp once, never overwrite" rule
 * {@code Shift#clockOutAt}/{@code PurchaseOrder#receivedAt} already established - the first time
 * status moves to CHECKED_OUT, not the most recent time.
 */
@Entity
@Table(name = "client_check_ins")
@Getter
@Setter
@NoArgsConstructor
public class ClientCheckIn extends BaseEntity {

    public enum Status {
        CHECKED_IN, CHECKED_OUT
    }

    public enum Method {
        MANUAL, KIOSK, MOBILE_APP, KEY_FOB
    }

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "contact_id", nullable = false)
    private UUID contactId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "checked_in_at", nullable = false)
    private Instant checkedInAt = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.CHECKED_IN;

    /** Stamped once, on entering CHECKED_OUT - see this class's javadoc. */
    @Column(name = "checked_out_at")
    private Instant checkedOutAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Method method = Method.MANUAL;

    @Column(length = 2000)
    private String notes;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public ClientCheckIn(UUID organizationId, UUID contactId, UUID ownerId) {
        this.organizationId = organizationId;
        this.contactId = contactId;
        this.ownerId = ownerId;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
