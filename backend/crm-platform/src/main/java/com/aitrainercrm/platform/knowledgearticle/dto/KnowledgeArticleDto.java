package com.aitrainercrm.platform.knowledgearticle.dto;

import com.aitrainercrm.platform.knowledgearticle.entity.KnowledgeArticle;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import lombok.Builder;

@Builder
public record KnowledgeArticleDto(
        UUID id,
        String title,
        String slug,
        String category,
        String content,
        KnowledgeArticle.Status status,
        int viewCount,
        Instant publishedAt,
        Set<String> tags,
        Instant createdAt,
        Instant updatedAt) {

    public static KnowledgeArticleDto from(KnowledgeArticle article) {
        return KnowledgeArticleDto.builder()
                .id(article.getId())
                .title(article.getTitle())
                .slug(article.getSlug())
                .category(article.getCategory())
                .content(article.getContent())
                .status(article.getStatus())
                .viewCount(article.getViewCount())
                .publishedAt(article.getPublishedAt())
                .tags(article.getTags())
                .createdAt(article.getCreatedAt())
                .updatedAt(article.getUpdatedAt())
                .build();
    }

    /** Header-only shape (no content body) for list endpoints - see KnowledgeArticleController#list. */
    public static KnowledgeArticleDto summaryFrom(KnowledgeArticle article) {
        return KnowledgeArticleDto.builder()
                .id(article.getId())
                .title(article.getTitle())
                .slug(article.getSlug())
                .category(article.getCategory())
                .status(article.getStatus())
                .viewCount(article.getViewCount())
                .publishedAt(article.getPublishedAt())
                .tags(article.getTags())
                .createdAt(article.getCreatedAt())
                .updatedAt(article.getUpdatedAt())
                .build();
    }
}
