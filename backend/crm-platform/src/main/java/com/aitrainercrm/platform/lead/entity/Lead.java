package com.aitrainercrm.platform.lead.entity;

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
 * An unqualified prospect - not yet trusted enough to have its own Account/
 * Contact/Opportunity rows. {@link #convert} (via LeadService) is the only
 * path that creates those; a lead that hasn't converted has none of
 * convertedAccountId/convertedContactId/convertedOpportunityId set.
 */
@Entity
@Table(name = "leads")
@Getter
@Setter
@NoArgsConstructor
public class Lead extends BaseEntity {

    public enum Status {
        NEW, CONTACTED, QUALIFIED, UNQUALIFIED, CONVERTED
    }

    public enum Source {
        WEBSITE, REFERRAL, COLD_CALL, EVENT, ADVERTISEMENT, OTHER
    }

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(length = 255)
    private String email;

    @Column(length = 30)
    private String phone;

    /** Free text on purpose - a lead's company isn't a tracked Account until (if ever) this lead converts. */
    @Column(name = "company_name", length = 200)
    private String companyName;

    @Column(length = 150)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Status status = Status.NEW;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Source source = Source.OTHER;

    @Column(length = 2000)
    private String description;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "converted_account_id")
    private UUID convertedAccountId;

    @Column(name = "converted_contact_id")
    private UUID convertedContactId;

    @Column(name = "converted_opportunity_id")
    private UUID convertedOpportunityId;

    @Column(name = "converted_at")
    private Instant convertedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public Lead(UUID organizationId, String firstName, String lastName, UUID ownerId) {
        this.organizationId = organizationId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.ownerId = ownerId;
    }

    public String getFullName() {
        return "%s %s".formatted(firstName, lastName).trim();
    }

    public boolean isConverted() {
        return convertedAt != null;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
