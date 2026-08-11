package com.aitrainercrm.platform.customfield.entity;

import com.aitrainercrm.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One (field, record) value pair - the EAV row backing whatever a
 * {@link CustomField} is attached to. {@code valueText} always stores the
 * value as text regardless of {@link CustomField.FieldType} (a NUMBER is
 * stored as e.g. {@code "42.5"}, a BOOLEAN as {@code "true"}, a DATE as its
 * ISO-8601 form) - {@code CustomFieldService} is responsible for
 * parsing/validating against the field's declared type on write and
 * re-parsing to the right JSON type on read; this entity itself has no
 * opinion about it, the same "store loosely-typed, validate at the service
 * boundary" tradeoff that keeps this table stable even if a new
 * {@code FieldType} is added later.
 *
 * <p>{@code recordId} is deliberately not a foreign key - see V10's
 * migration comment for why (it points at whichever table the owning
 * {@code CustomField}'s target says it should, standard entity or
 * {@link CustomObjectRecord}, and no single FK could express that).
 */
@Entity
@Table(name = "custom_field_values")
@Getter
@Setter
@NoArgsConstructor
public class CustomFieldValue extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "custom_field_id", nullable = false)
    private UUID customFieldId;

    @Column(name = "record_id", nullable = false)
    private UUID recordId;

    @Column(name = "value_text", columnDefinition = "text")
    private String valueText;

    public CustomFieldValue(UUID organizationId, UUID customFieldId, UUID recordId, String valueText) {
        this.organizationId = organizationId;
        this.customFieldId = customFieldId;
        this.recordId = recordId;
        this.valueText = valueText;
    }
}
