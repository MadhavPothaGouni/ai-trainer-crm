package com.aitrainercrm.platform.macro.entity;

import com.aitrainercrm.platform.common.entity.BaseEntity;
import com.aitrainercrm.platform.ticket.entity.Ticket;
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
 * A reusable canned response a rep applies to a {@link Ticket} - see V34's migration comment for
 * the module overview and for why {@link com.aitrainercrm.platform.macro.service.MacroService#apply}
 * is built the way it is (calling straight through {@code TicketService}'s own public methods
 * rather than re-implementing Ticket's per-record authorization here). Mirrors {@code Product}'s
 * no-OWN catalog shape exactly: shared organization content, no {@code ownerId}, TEAM/DEPARTMENT/
 * ORGANIZATION scope only.
 */
@Entity
@Table(name = "macros")
@Getter
@Setter
@NoArgsConstructor
public class Macro extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 2000)
    private String body;

    /** Null means applying this macro never changes the ticket's status - see MacroService#apply's javadoc. */
    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", length = 20)
    private Ticket.Status newStatus;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public Macro(UUID organizationId, String name, String body) {
        this.organizationId = organizationId;
        this.name = name;
        this.body = body;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
