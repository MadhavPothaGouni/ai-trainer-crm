package com.aitrainercrm.platform.account.entity;

import com.aitrainercrm.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A company the organization sells to (or is trying to) - the "who" every {@link com.aitrainercrm.platform.contact.entity.Contact} and {@link com.aitrainercrm.platform.opportunity.entity.Opportunity} ultimately belongs to. */
@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor
public class Account extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 100)
    private String industry;

    @Column(length = 255)
    private String website;

    @Column(length = 30)
    private String phone;

    @Column(name = "billing_street", length = 255)
    private String billingStreet;

    @Column(name = "billing_city", length = 100)
    private String billingCity;

    @Column(name = "billing_state", length = 100)
    private String billingState;

    @Column(name = "billing_postal_code", length = 20)
    private String billingPostalCode;

    @Column(name = "billing_country", length = 100)
    private String billingCountry;

    @Column(name = "annual_revenue", precision = 15, scale = 2)
    private BigDecimal annualRevenue;

    @Column(name = "employee_count")
    private Integer employeeCount;

    @Column(length = 2000)
    private String description;

    /** Plain id, not a JPA relationship - see V3's migration comment for why every CRM cross-reference in this schema is a resolved-in-service-layer uuid column. */
    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    /** Soft delete: an Account can be referenced by contacts/opportunities/leads that should keep their history even after the account itself is "removed." */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    public Account(UUID organizationId, String name, UUID ownerId) {
        this.organizationId = organizationId;
        this.name = name;
        this.ownerId = ownerId;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
