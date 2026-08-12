package com.aitrainercrm.platform.role.entity;

import com.aitrainercrm.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single (resource, action, scope) grant, e.g. (LEAD, CREATE, TEAM) -
 * "can create leads owned by their own team." Permissions are assigned to
 * {@link Role}s, never directly to a {@link com.aitrainercrm.platform.user.entity.User};
 * a user's effective permission set is the union of every role they hold.
 *
 * <p>The permission catalog itself (which (resource, action) pairs exist)
 * is seeded by a Flyway migration, not created ad hoc through the API -
 * the set of protectable resources is part of the platform's shape, not
 * tenant-configurable data.
 */
@Entity
@Table(
        name = "permissions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"resource", "action", "scope"}))
@Getter
@Setter
@NoArgsConstructor
public class Permission extends BaseEntity {

    public enum Resource {
        LEAD, CONTACT, ACCOUNT, OPPORTUNITY, ACTIVITY, PRODUCT, QUOTE, ORDER, INVOICE, PAYMENT,
        CAMPAIGN, TICKET, KNOWLEDGE_ARTICLE, WORKFLOW, REPORT, DASHBOARD, USER, ROLE, ORGANIZATION,
        AUDIT_LOG, INTEGRATION, API_KEY, CUSTOM_FIELD, CUSTOM_OBJECT, EMAIL_MESSAGE, CALENDAR_EVENT, TEAM,
        ATTACHMENT
    }

    public enum Action {
        CREATE, READ, UPDATE, DELETE, EXPORT, IMPORT, ASSIGN, APPROVE, MANAGE
    }

    /** How far a grant of this permission reaches. */
    public enum Scope {
        OWN, TEAM, DEPARTMENT, ORGANIZATION
    }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Resource resource;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Action action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Scope scope;

    @Column(nullable = false, length = 200)
    private String description;

    public Permission(Resource resource, Action action, Scope scope, String description) {
        this.resource = resource;
        this.action = action;
        this.scope = scope;
        this.description = description;
    }

    /** The stable string form ("LEAD:CREATE:TEAM") used as a Spring Security authority name. */
    public String toAuthorityName() {
        return "%s:%s:%s".formatted(resource, action, scope);
    }
}
