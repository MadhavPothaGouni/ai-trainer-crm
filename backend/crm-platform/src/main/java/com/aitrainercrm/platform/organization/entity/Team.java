package com.aitrainercrm.platform.organization.entity;

import com.aitrainercrm.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    public Team(UUID organizationId, String name, String department) {
        this.organizationId = organizationId;
        this.name = name;
        this.department = department;
    }
}
