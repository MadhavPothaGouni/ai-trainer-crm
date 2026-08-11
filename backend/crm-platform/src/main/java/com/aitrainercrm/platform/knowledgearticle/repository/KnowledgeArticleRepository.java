package com.aitrainercrm.platform.knowledgearticle.repository;

import com.aitrainercrm.platform.knowledgearticle.entity.KnowledgeArticle;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KnowledgeArticleRepository extends JpaRepository<KnowledgeArticle, UUID> {

    @Query("select a from KnowledgeArticle a where a.id = :id and a.organizationId = :organizationId and a.deletedAt is null")
    Optional<KnowledgeArticle> findActiveByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    Page<KnowledgeArticle> findByOrganizationIdAndDeletedAtIsNull(UUID organizationId, Pageable pageable);

    Page<KnowledgeArticle> findByOrganizationIdAndCategoryAndDeletedAtIsNull(UUID organizationId, String category, Pageable pageable);

    List<KnowledgeArticle> findByOrganizationIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID organizationId);

    boolean existsByOrganizationIdAndSlug(UUID organizationId, String slug);
}
