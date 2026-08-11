package com.aitrainercrm.platform.ticket.entity;

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
 * A support request, optionally tied to an {@link com.aitrainercrm.platform.account.entity.Account}
 * and/or {@link com.aitrainercrm.platform.contact.entity.Contact} - the resource this whole module
 * exists to fill in; see V14's migration comment for how the gap was found (a full permission set
 * seeded in V2 with no implementation anywhere) and why this mirrors Account/Contact/Lead's
 * owner-scoped shape rather than inventing a new one.
 */
@Entity
@Table(name = "tickets")
@Getter
@Setter
@NoArgsConstructor
public class Ticket extends BaseEntity {

    public enum Status {
        OPEN, IN_PROGRESS, RESOLVED, CLOSED
    }

    public enum Priority {
        LOW, MEDIUM, HIGH, URGENT
    }

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    /** Nullable - a ticket can be raised before either is on file, same reasoning {@code Contact.accountId} being nullable already established. */
    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "contact_id")
    private UUID contactId;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Priority priority = Priority.MEDIUM;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    /** Set when {@code status} moves to RESOLVED or CLOSED, cleared if it moves back - see TicketService#updateStatus and V14's migration comment for why this isn't a one-way transition. */
    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public Ticket(UUID organizationId, String subject, UUID ownerId) {
        this.organizationId = organizationId;
        this.subject = subject;
        this.ownerId = ownerId;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
