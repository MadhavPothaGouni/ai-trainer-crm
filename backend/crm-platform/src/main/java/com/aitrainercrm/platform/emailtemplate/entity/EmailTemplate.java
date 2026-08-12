package com.aitrainercrm.platform.emailtemplate.entity;

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
 * A reusable, organization-wide email {@code subject}/{@code body} pair with {@code {{token}}}
 * placeholders - see V27's migration comment for why, like {@link
 * com.aitrainercrm.platform.product.entity.Product}, there's no {@code ownerId}: a template is
 * shared organization content, not something one rep owns, so {@code EmailTemplateService} does no
 * {@link com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService} check at all -
 * holding any of EMAIL_TEMPLATE's three seeded scopes (TEAM/DEPARTMENT/ORGANIZATION, no OWN) grants
 * that action against every template in the org.
 *
 * <p>{@code subject}/{@code body} are opaque text as far as this entity and its repository are
 * concerned - {@code emailtemplate.render.TemplateRenderer} is the only thing that ever parses
 * {@code {{token}}} placeholders out of them, and only at render time, never at save time. There is
 * deliberately no validation here that a template's placeholders actually resolve to anything -
 * see {@code TemplateRenderer}'s javadoc for why an unresolved token is left untouched rather than
 * rejected at save time or blanked at render time.
 */
@Entity
@Table(name = "email_templates")
@Getter
@Setter
@NoArgsConstructor
public class EmailTemplate extends BaseEntity {

    public enum Category {
        GENERAL, SALES, SUPPORT, MARKETING
    }

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Category category = Category.GENERAL;

    @Column(nullable = false, length = 300)
    private String subject;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public EmailTemplate(UUID organizationId, String name, Category category, String subject, String body) {
        this.organizationId = organizationId;
        this.name = name;
        this.category = category;
        this.subject = subject;
        this.body = body;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
