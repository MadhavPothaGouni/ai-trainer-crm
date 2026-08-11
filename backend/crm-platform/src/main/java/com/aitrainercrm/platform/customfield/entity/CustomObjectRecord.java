package com.aitrainercrm.platform.customfield.entity;

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
 * A single row of data under a {@link CustomObject} - the custom-object
 * equivalent of an Account or Contact row, except the only field this
 * platform hard-codes is {@code name}; everything else comes from whatever
 * {@link CustomField}s an admin has attached to the parent object, stored
 * as {@link CustomFieldValue} rows keyed by this record's id.
 *
 * <p>Soft-deleted via {@code deletedAt} (not a hard delete) for the same
 * reason every other CRM record in this platform is - an admin removing a
 * custom object entirely cascades (see V10's FK), but removing one record
 * of it should be recoverable/auditable, not silently gone.
 */
@Entity
@Table(name = "custom_object_records")
@Getter
@Setter
@NoArgsConstructor
public class CustomObjectRecord extends BaseEntity {

    @Column(name = "custom_object_id", nullable = false)
    private UUID customObjectId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 300)
    private String name;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public CustomObjectRecord(UUID customObjectId, UUID organizationId, String name) {
        this.customObjectId = customObjectId;
        this.organizationId = organizationId;
        this.name = name;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
