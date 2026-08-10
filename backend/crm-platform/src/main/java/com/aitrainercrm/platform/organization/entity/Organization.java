package com.aitrainercrm.platform.organization.entity;

import com.aitrainercrm.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** The tenant boundary: every user, team, lead, contact, etc. belongs to exactly one Organization. */
@Entity
@Table(name = "organizations")
@Getter
@Setter
@NoArgsConstructor
public class Organization extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String name;

    @Column(unique = true, length = 100)
    private String slug;

    @Column(length = 3)
    private String defaultCurrency = "USD";

    @Column(length = 60)
    private String timezone = "UTC";

    @Column(name = "fiscal_year_start_month", nullable = false)
    private int fiscalYearStartMonth = 1;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public Organization(String name, String slug) {
        this.name = name;
        this.slug = slug;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
