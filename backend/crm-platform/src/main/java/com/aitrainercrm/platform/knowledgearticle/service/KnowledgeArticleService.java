package com.aitrainercrm.platform.knowledgearticle.service;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.common.exception.BusinessException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.common.util.CsvWriter;
import com.aitrainercrm.platform.knowledgearticle.dto.CreateKnowledgeArticleRequest;
import com.aitrainercrm.platform.knowledgearticle.dto.UpdateKnowledgeArticleRequest;
import com.aitrainercrm.platform.knowledgearticle.entity.KnowledgeArticle;
import com.aitrainercrm.platform.knowledgearticle.repository.KnowledgeArticleRepository;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Knowledge-base articles. Same shared-org-resource pattern as {@code
 * CampaignService}/{@code ProductService} - no {@code
 * ScopeAuthorizationService} calls; the controller's static
 * {@code @PreAuthorize} is the whole authorization story.
 */
@Service
@RequiredArgsConstructor
public class KnowledgeArticleService {

    private final KnowledgeArticleRepository articleRepository;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<KnowledgeArticle> list(UserPrincipal principal, String category, Pageable pageable) {
        UUID organizationId = principal.getOrganizationId();
        if (category != null && !category.isBlank()) {
            return articleRepository.findByOrganizationIdAndCategoryAndDeletedAtIsNull(organizationId, category, pageable);
        }
        return articleRepository.findByOrganizationIdAndDeletedAtIsNull(organizationId, pageable);
    }

    /**
     * Deliberately not {@code readOnly} - every call stamps a view. See
     * {@link KnowledgeArticle}'s javadoc for why this codebase accepts the
     * simplicity of "every fetch counts" over building out per-viewer
     * dedup for an internal tool that doesn't need it.
     */
    @Transactional
    public KnowledgeArticle get(UserPrincipal principal, UUID articleId) {
        KnowledgeArticle article = findOrThrow(principal.getOrganizationId(), articleId);
        article.setViewCount(article.getViewCount() + 1);
        articleRepository.save(article);
        return article;
    }

    /** Backs GET /knowledge-articles/export (KNOWLEDGE_ARTICLE:EXPORT) - see CampaignService#exportCsv's javadoc for why this is notable at all. Doesn't bump viewCount - an export isn't a read of any one article. */
    @Transactional(readOnly = true)
    public byte[] exportCsv(UserPrincipal principal) {
        List<KnowledgeArticle> articles = articleRepository.findByOrganizationIdAndDeletedAtIsNullOrderByCreatedAtDesc(principal.getOrganizationId());
        CsvWriter csv = new CsvWriter().row("Title", "Slug", "Category", "Status", "Tags", "View Count", "Published At", "Created At");
        for (KnowledgeArticle article : articles) {
            csv.row(
                    article.getTitle(), article.getSlug(), article.getCategory(), article.getStatus(),
                    String.join("; ", article.getTags()), article.getViewCount(), article.getPublishedAt(), article.getCreatedAt());
        }
        return csv.toBytes();
    }

    @Transactional
    public KnowledgeArticle create(UserPrincipal principal, CreateKnowledgeArticleRequest request) {
        UUID organizationId = principal.getOrganizationId();
        String slug = generateUniqueSlug(organizationId, request.title());

        KnowledgeArticle article = new KnowledgeArticle(organizationId, request.title(), slug, request.content());
        article.setCategory(request.category());
        article.setTags(nullToEmpty(request.tags()));
        articleRepository.save(article);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), organizationId, "KnowledgeArticle", article.getId()));
        return article;
    }

    @Transactional
    public KnowledgeArticle update(UserPrincipal principal, UUID articleId, UpdateKnowledgeArticleRequest request) {
        KnowledgeArticle article = findOrThrow(principal.getOrganizationId(), articleId);

        article.setTitle(request.title());
        article.setCategory(request.category());
        article.setContent(request.content());
        article.setTags(nullToEmpty(request.tags()));
        articleRepository.save(article);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "KnowledgeArticle", article.getId()));
        return article;
    }

    /** DRAFT -&gt; PUBLISHED only; stamps publishedAt. */
    @Transactional
    public KnowledgeArticle publish(UserPrincipal principal, UUID articleId) {
        KnowledgeArticle article = findOrThrow(principal.getOrganizationId(), articleId);
        if (article.getStatus() != KnowledgeArticle.Status.DRAFT) {
            throw new BusinessException(
                    "ARTICLE_INVALID_STATUS_TRANSITION", "Only a DRAFT article can be published (was " + article.getStatus() + ")", HttpStatus.CONFLICT);
        }

        article.setStatus(KnowledgeArticle.Status.PUBLISHED);
        article.setPublishedAt(Instant.now());
        articleRepository.save(article);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "KnowledgeArticle", article.getId()));
        return article;
    }

    /** DRAFT or PUBLISHED -&gt; ARCHIVED; terminal - there's no un-archiving in this module. */
    @Transactional
    public KnowledgeArticle archive(UserPrincipal principal, UUID articleId) {
        KnowledgeArticle article = findOrThrow(principal.getOrganizationId(), articleId);
        if (article.getStatus() == KnowledgeArticle.Status.ARCHIVED) {
            throw new BusinessException("ARTICLE_INVALID_STATUS_TRANSITION", "This article is already ARCHIVED", HttpStatus.CONFLICT);
        }

        article.setStatus(KnowledgeArticle.Status.ARCHIVED);
        articleRepository.save(article);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "KnowledgeArticle", article.getId()));
        return article;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID articleId) {
        KnowledgeArticle article = findOrThrow(principal.getOrganizationId(), articleId);
        article.setDeletedAt(Instant.now());
        articleRepository.save(article);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "KnowledgeArticle", articleId));
    }

    private KnowledgeArticle findOrThrow(UUID organizationId, UUID articleId) {
        return articleRepository.findActiveByIdAndOrganizationId(articleId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("KnowledgeArticle", articleId));
    }

    private Set<String> nullToEmpty(Set<String> tags) {
        return tags == null ? new HashSet<>() : new HashSet<>(tags);
    }

    /**
     * Lowercases, replaces every run of non-alphanumeric characters with a
     * single hyphen, and trims leading/trailing hyphens - "Q3 Rollout: EMEA!"
     * becomes "q3-rollout-emea". If that collides with an existing slug in
     * this organization (two articles titled "Getting Started", say),
     * appends "-2", "-3", ... until it's unique, checking the DB each time
     * rather than trying to guess a free suffix - this only runs once per
     * article at creation time, so the extra round trips on a collision are
     * a non-issue.
     */
    private String generateUniqueSlug(UUID organizationId, String title) {
        String base = title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+)|(-+$)", "");
        if (base.isEmpty()) {
            base = "article";
        }
        String candidate = base;
        int suffix = 2;
        while (articleRepository.existsByOrganizationIdAndSlug(organizationId, candidate)) {
            candidate = base + "-" + suffix;
            suffix++;
        }
        return candidate;
    }
}
