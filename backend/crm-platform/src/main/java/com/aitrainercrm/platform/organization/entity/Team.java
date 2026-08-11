package com.aitrainercrm.platform.organization.entity;

import com.aitrainercrm.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Existed since V1__init_schema.sql purely so {@link
 * com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService}
 * had something to resolve TEAM/DEPARTMENT-scope visibility against, with no
 * management API of its own - see V16's migration comment for how that gap
 * was found and closed. {@link #deletedAt} was added in V16 alongside the
 * first delete endpoint that could ever need it (a team can't be hard-deleted
 * without either orphaning or FK-blocking on any user still pointing at it
 * via {@code User#teamId}).
 */
@Entity
@Table(name = "teams")
@Getter
@Setter
@NoArgsConstructor
public class Team extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 150)
    private String name;

    /** Sales, Marketing, Support, Finance, ... - free text on purpose; teams are user-defined, not a fixed enum. */
    @Column(length = 100)
    private String department;

    @Column(name = "lead_user_id")
    private UUID leadUserId;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public Team(UUID organizationId, String name, String department) {
        this.organizationId = organizationId;
        this.name = name;
        this.department = department;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
