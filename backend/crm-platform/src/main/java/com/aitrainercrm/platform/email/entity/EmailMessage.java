package com.aitrainercrm.platform.email.entity;

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
 * A logged email (sent or received) against exactly one Account/Contact/
 * Opportunity/Lead/Ticket. See V15's migration comment for why this is a
 * genuinely new resource rather than a permission-catalog gap like Ticket
 * was, and why it exists alongside Activity's EMAIL type rather than
 * replacing it - Activity logs "an email happened," EmailMessage captures
 * what the email actually was (who it went to, which direction, when it was
 * sent).
 *
 * <p>Owner-scoped exactly like {@code Ticket}: soft-deletable, OWN/TEAM/
 * DEPARTMENT/ORGANIZATION authorized via {@code ScopeAuthorizationService}.
 * {@link #relatedToId} has no JPA relationship or DB foreign key, same
 * reasoning as {@code Activity#relatedToId} (V4's migration comment) - a
 * single column can't target five different tables, so {@code
 * EmailMessageService} resolves and validates it explicitly against
 * whichever repository {@link #relatedToType} names.
 */
@Entity
@Table(name = "email_messages")
@Getter
@Setter
@NoArgsConstructor
public class EmailMessage extends BaseEntity {

    public enum Direction {
        INBOUND, OUTBOUND
    }

    public enum RelatedToType {
        ACCOUNT, CONTACT, OPPORTUNITY, LEAD, TICKET
    }

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Direction direction;

    @Column(nullable = false, length = 500)
    private String subject;

    @Column(length = 10000)
    private String body;

    @Column(name = "from_address", nullable = false, length = 255)
    private String fromAddress;

    /** Comma-separated - see V15's migration comment for why this isn't a native array column or a child table. */
    @Column(name = "to_addresses", nullable = false, length = 2000)
    private String toAddresses;

    @Column(name = "cc_addresses", length = 2000)
    private String ccAddresses;

    @Enumerated(EnumType.STRING)
    @Column(name = "related_to_type", nullable = false, length = 20)
    private RelatedToType relatedToType;

    @Column(name = "related_to_id", nullable = false)
    private UUID relatedToId;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public EmailMessage(
            UUID organizationId, Direction direction, String subject, String fromAddress, String toAddresses,
            RelatedToType relatedToType, UUID relatedToId, Instant sentAt, UUID ownerId) {
        this.organizationId = organizationId;
        this.direction = direction;
        this.subject = subject;
        this.fromAddress = fromAddress;
        this.toAddresses = toAddresses;
        this.relatedToType = relatedToType;
        this.relatedToId = relatedToId;
        this.sentAt = sentAt;
        this.ownerId = ownerId;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
