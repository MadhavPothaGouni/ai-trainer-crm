package com.aitrainercrm.platform.customfield.entity;

import com.aitrainercrm.platform.common.entity.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An admin-defined extra field, attached to either one of a fixed allow-list
 * of standard CRM entities ({@link StandardEntityType}) or to a
 * {@link CustomObject} - never both, never neither, enforced by V10's check
 * constraint and mirrored here by {@link #hasValidTarget()}, the same
 * exactly-one-of-two-nullable-columns pattern {@code CampaignMember}
 * introduced for lead/contact in V9.
 *
 * <p>Unlike {@code CampaignMember}, there's no FK for
 * {@code standardEntityType} - "ACCOUNT" isn't a row in any table, it's a
 * fixed enum this platform ships with, so uniqueness/validity is checked in
 * {@code CustomFieldService} against {@link StandardEntityType#values()}
 * rather than by referential integrity.
 *
 * <p>{@code picklistValues} is {@code FetchType.EAGER} for the same reason
 * {@code KnowledgeArticle#tags} is: small, always-needed-together-with-the-
 * field-definition collection, so a DTO mapper running outside the
 * originating {@code @Transactional} method (e.g. from the controller) can
 * safely read it without a {@code LazyInitializationException}. It's only
 * meaningful when {@code fieldType == PICKLIST} - {@code CustomFieldService}
 * rejects a create/update where a non-PICKLIST field has entries here, or a
 * PICKLIST field has none.
 */
@Entity
@Table(name = "custom_fields")
@Getter
@Setter
@NoArgsConstructor
public class CustomField extends BaseEntity {

    /** The fixed set of standard CRM entities an admin may attach a custom field to. */
    public enum StandardEntityType {
        ACCOUNT, CONTACT, LEAD, OPPORTUNITY, CAMPAIGN
    }

    public enum FieldType {
        TEXT, TEXT_AREA, NUMBER, DATE, BOOLEAN, PICKLIST
    }

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "standard_entity_type", length = 30)
    private StandardEntityType standardEntityType;

    @Column(name = "custom_object_id")
    private UUID customObjectId;

    @Column(name = "api_name", nullable = false, length = 80)
    private String apiName;

    @Column(nullable = false, length = 150)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "field_type", nullable = false, length = 20)
    private FieldType fieldType;

    @Column(nullable = false)
    private boolean required = false;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Column(nullable = false)
    private boolean active = true;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "custom_field_picklist_values", joinColumns = @JoinColumn(name = "custom_field_id"))
    @OrderColumn(name = "display_order")
    @Column(name = "value", length = 100)
    private List<String> picklistValues = new ArrayList<>();

    public CustomField(UUID organizationId, StandardEntityType standardEntityType, UUID customObjectId, String apiName, String label, FieldType fieldType) {
        this.organizationId = organizationId;
        this.standardEntityType = standardEntityType;
        this.customObjectId = customObjectId;
        this.apiName = apiName;
        this.label = label;
        this.fieldType = fieldType;
    }

    /** Mirrors V10's {@code chk_custom_fields_exactly_one_target} check constraint. */
    public boolean hasValidTarget() {
        return (standardEntityType != null) != (customObjectId != null);
    }
}
