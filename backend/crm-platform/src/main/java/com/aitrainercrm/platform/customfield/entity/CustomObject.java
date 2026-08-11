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
 * An admin-defined custom entity type - "Project", "Asset", "Warranty
 * Claim", whatever this organization needs that isn't one of the built-in
 * CRM resources. A {@code CustomObject} is just the schema/label; the
 * actual rows live in {@link CustomObjectRecord}, and whatever extra data
 * each record carries comes entirely from {@link CustomField}s attached to
 * this object's id (see {@code CustomField#customObjectId}) plus their
 * {@link CustomFieldValue}s - there's no generic "columns" concept here
 * beyond the one built-in {@code name} field every record gets.
 *
 * <p>{@code apiName} is the stable, lowercase-with-underscores identifier
 * used everywhere a custom field needs to reference "this object" (URLs,
 * {@code CustomField#customObjectId} indirectly, CSV export headers) -
 * {@code label}/{@code pluralLabel} are what the frontend actually
 * displays and can be renamed freely without touching apiName, the same
 * stable-slug-vs-editable-title tradeoff {@code KnowledgeArticle#slug}
 * makes.
 */
@Entity
@Table(name = "custom_objects")
@Getter
@Setter
@NoArgsConstructor
public class CustomObject extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "api_name", nullable = false, length = 80)
    private String apiName;

    @Column(nullable = false, length = 150)
    private String label;

    @Column(name = "plural_label", nullable = false, length = 150)
    private String pluralLabel;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private boolean active = true;

    public CustomObject(UUID organizationId, String apiName, String label, String pluralLabel) {
        this.organizationId = organizationId;
        this.apiName = apiName;
        this.label = label;
        this.pluralLabel = pluralLabel;
    }
}
