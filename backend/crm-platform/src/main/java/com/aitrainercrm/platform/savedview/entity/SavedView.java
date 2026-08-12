package com.aitrainercrm.platform.savedview.entity;

import com.aitrainercrm.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A named, personal filter+sort combination for one of the standard CRM list pages - see V26's
 * migration comment for why this skips the permission catalog entirely (the purest instance of
 * the fourth-kind, notification-style self-scoped shape this session has built: every single
 * action is scoped to {@link #ownerUserId}, not just some of them).
 *
 * <p>{@link #filters} is an opaque, free-form JSON blob - {@code SavedViewService} never parses
 * or validates its contents, only that it's non-blank; the frontend owns the shape, which varies
 * by {@link #entityType}.
 */
@Entity
@Table(name = "saved_views")
@Getter
@Setter
@NoArgsConstructor
public class SavedView extends BaseEntity {

    public enum EntityType {
        LEAD, CONTACT, ACCOUNT, OPPORTUNITY, TICKET
    }

    public enum SortDirection {
        ASC, DESC
    }

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 20)
    private EntityType entityType;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, columnDefinition = "text")
    private String filters;

    @Column(name = "sort_field", length = 50)
    private String sortField;

    @Enumerated(EnumType.STRING)
    @Column(name = "sort_direction", length = 10)
    private SortDirection sortDirection;

    // Named without an "is" prefix, same reasoning Dashboard#defaultDashboard documents - keeps
    // Lombok's generated isDefaultView()/setDefaultView(...) predictable instead of the
    // inconsistent isDefault()/setDefault(...) a field literally named isDefault would produce.
    @Column(name = "is_default", nullable = false)
    private boolean defaultView = false;

    public SavedView(UUID organizationId, UUID ownerUserId, EntityType entityType, String name, String filters) {
        this.organizationId = organizationId;
        this.ownerUserId = ownerUserId;
        this.entityType = entityType;
        this.name = name;
        this.filters = filters;
    }
}
