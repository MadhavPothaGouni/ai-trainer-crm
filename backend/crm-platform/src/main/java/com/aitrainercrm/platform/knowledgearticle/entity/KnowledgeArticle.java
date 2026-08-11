package com.aitrainercrm.platform.knowledgearticle.entity;

import com.aitrainercrm.platform.common.entity.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A support/help-center article. Like {@link com.aitrainercrm.platform.campaign.entity.Campaign},
 * there's no {@code ownerId} - KNOWLEDGE_ARTICLE is seeded in V2 at TEAM/
 * DEPARTMENT/ORGANIZATION scope only, so {@code KnowledgeArticleService}
 * does no per-record {@code ScopeAuthorizationService} check.
 *
 * <p>{@link #slug} is derived from {@link #title} at create time
 * ({@code KnowledgeArticleService#generateUniqueSlug}) and never changes on
 * an update even if the title does - a stable URL matters more for a
 * knowledge-base article than always matching the current title verbatim,
 * the same tradeoff most blogging/CMS platforms make.
 *
 * <p>{@link #status} moves DRAFT -&gt; PUBLISHED -&gt; ARCHIVED; ARCHIVED can
 * also be reached directly from DRAFT (killing a draft nobody ever
 * published). There's no APPROVE action seeded for KNOWLEDGE_ARTICLE either,
 * so both transitions are plain {@code KNOWLEDGE_ARTICLE:UPDATE}.
 *
 * <p>{@link #viewCount} increments on every {@code KnowledgeArticleService#get}
 * call, deliberately simple - this is an internal knowledge base, not a
 * public site worth guarding against double-counting from the same reader
 * refreshing the page, so there's no per-viewer dedup here.
 *
 * <p>{@link #tags} is {@code EAGER}, not {@code LAZY} like {@code
 * Role.permissions} - a deliberately different call than that one. {@code
 * Role.permissions} is skippable on plenty of fetches (plain role-management
 * list screens never touch it), but every screen that shows an article at
 * all - a list row, a detail page, a tag filter - wants its tags, and the
 * set is always small, so eagerly joining it here isn't the N+1-shaped
 * tradeoff EAGER would be on {@code Role.permissions}. It also sidesteps
 * the exact bug {@code ApiKeyService#authenticate} hit earlier this
 * session: a lazy collection touched by a DTO mapper running after the
 * {@code @Transactional} service method that fetched the entity has already
 * returned throws {@code LazyInitializationException} - EAGER here means
 * {@code KnowledgeArticleDto.from} can safely be called from the controller
 * instead of needing every service method to build the DTO itself.
 */
@Entity
@Table(name = "knowledge_articles")
@Getter
@Setter
@NoArgsConstructor
public class KnowledgeArticle extends BaseEntity {

    public enum Status {
        DRAFT, PUBLISHED, ARCHIVED
    }

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(nullable = false, length = 320)
    private String slug;

    @Column(length = 100)
    private String category;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.DRAFT;

    @Column(name = "view_count", nullable = false)
    private int viewCount = 0;

    @Column(name = "published_at")
    private Instant publishedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "knowledge_article_tags", joinColumns = @JoinColumn(name = "knowledge_article_id"))
    @Column(name = "tag", length = 50)
    private Set<String> tags = new HashSet<>();

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public KnowledgeArticle(UUID organizationId, String title, String slug, String content) {
        this.organizationId = organizationId;
        this.title = title;
        this.slug = slug;
        this.content = content;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
