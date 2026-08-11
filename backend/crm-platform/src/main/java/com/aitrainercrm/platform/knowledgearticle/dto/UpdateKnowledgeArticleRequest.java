package com.aitrainercrm.platform.knowledgearticle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record UpdateKnowledgeArticleRequest(
        @NotBlank @Size(max = 300) String title,
        @Size(max = 100) String category,
        @NotBlank String content,
        Set<@Size(max = 50) String> tags) {
}
