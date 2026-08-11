package com.aitrainercrm.platform.knowledgearticle.dto;

import com.aitrainercrm.platform.knowledgearticle.entity.KnowledgeArticle;
import jakarta.validation.constraints.NotNull;

/** Drives KnowledgeArticleService#publish (-&gt; PUBLISHED) and #archive (-&gt; ARCHIVED); this request type exists for symmetry with Order/Invoice/Quote's status PATCH but only ever carries one of those two values in practice. */
public record UpdateKnowledgeArticleStatusRequest(@NotNull KnowledgeArticle.Status status) {
}
