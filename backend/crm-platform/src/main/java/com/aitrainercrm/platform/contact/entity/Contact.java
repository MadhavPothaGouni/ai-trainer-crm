package com.aitrainercrm.platform.contact.entity;

import com.aitrainercrm.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A person - usually, but not necessarily, at an {@link com.aitrainercrm.platform.account.entity.Account}. */
@Entity
@Table(name = "contacts")
@Getter
@Setter
@NoArgsConstructor
public class Contact extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    /** Nullable on purpose: a contact can exist before (or entirely without) a company being tracked as an Account. */
    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(length = 255)
    private String email;

    @Column(length = 30)
    private String phone;

    @Column(length = 150)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public Contact(UUID organizationId, String firstName, String lastName, UUID ownerId) {
        this.organizationId = organizationId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.ownerId = ownerId;
    }

    public String getFullName() {
        return "%s %s".formatted(firstName, lastName).trim();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
