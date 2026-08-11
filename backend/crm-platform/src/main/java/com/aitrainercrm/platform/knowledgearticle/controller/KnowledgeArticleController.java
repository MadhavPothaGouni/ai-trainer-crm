package com.aitrainercrm.platform.knowledgearticle.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.knowledgearticle.dto.CreateKnowledgeArticleRequest;
import com.aitrainercrm.platform.knowledgearticle.dto.KnowledgeArticleDto;
import com.aitrainercrm.platform.knowledgearticle.dto.UpdateKnowledgeArticleRequest;
import com.aitrainercrm.platform.knowledgearticle.entity.KnowledgeArticle;
import com.aitrainercrm.platform.knowledgearticle.service.KnowledgeArticleService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** No OWN scope and no APPROVE action on KNOWLEDGE_ARTICLE (see KnowledgeArticleService's javadoc) - every @PreAuthorize here only lists TEAM/DEPARTMENT/ORGANIZATION. */
@RestController
@RequestMapping("/api/v1/knowledge-articles")
@RequiredArgsConstructor
public class KnowledgeArticleController {

    private final KnowledgeArticleService articleService;

    /** category narrows to one category's articles; omit it for the flat org-wide list. List rows use the summary shape (no content body) - see KnowledgeArticleDto#summaryFrom. */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('KNOWLEDGE_ARTICLE:READ:TEAM','KNOWLEDGE_ARTICLE:READ:DEPARTMENT','KNOWLEDGE_ARTICLE:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<KnowledgeArticleDto>> list(
            @RequestParam(required = false) String category, Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<KnowledgeArticle> page = articleService.list(principal, category, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(KnowledgeArticleDto::summaryFrom).toList()));
    }

    /** KNOWLEDGE_ARTICLE:EXPORT-gated - see CampaignController#export's javadoc for why this endpoint exists at all. */
    @GetMapping("/export")
    @PreAuthorize("hasAnyAuthority('KNOWLEDGE_ARTICLE:EXPORT:TEAM','KNOWLEDGE_ARTICLE:EXPORT:DEPARTMENT','KNOWLEDGE_ARTICLE:EXPORT:ORGANIZATION')")
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal UserPrincipal principal) {
        byte[] csv = articleService.exportCsv(principal);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("knowledge-articles.csv").build().toString())
                .body(csv);
    }

    /** Increments the article's viewCount every call - see KnowledgeArticleService#get's javadoc. */
    @GetMapping("/{articleId}")
    @PreAuthorize("hasAnyAuthority('KNOWLEDGE_ARTICLE:READ:TEAM','KNOWLEDGE_ARTICLE:READ:DEPARTMENT','KNOWLEDGE_ARTICLE:READ:ORGANIZATION')")
    public ApiResponse<KnowledgeArticleDto> get(@PathVariable UUID articleId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(KnowledgeArticleDto.from(articleService.get(principal, articleId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('KNOWLEDGE_ARTICLE:CREATE:TEAM','KNOWLEDGE_ARTICLE:CREATE:DEPARTMENT','KNOWLEDGE_ARTICLE:CREATE:ORGANIZATION')")
    public ApiResponse<KnowledgeArticleDto> create(
            @Valid @RequestBody CreateKnowledgeArticleRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(KnowledgeArticleDto.from(articleService.create(principal, request)), "Article created");
    }

    @PutMapping("/{articleId}")
    @PreAuthorize("hasAnyAuthority('KNOWLEDGE_ARTICLE:UPDATE:TEAM','KNOWLEDGE_ARTICLE:UPDATE:DEPARTMENT','KNOWLEDGE_ARTICLE:UPDATE:ORGANIZATION')")
    public ApiResponse<KnowledgeArticleDto> update(
            @PathVariable UUID articleId, @Valid @RequestBody UpdateKnowledgeArticleRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(KnowledgeArticleDto.from(articleService.update(principal, articleId, request)), "Article updated");
    }

    @PostMapping("/{articleId}/publish")
    @PreAuthorize("hasAnyAuthority('KNOWLEDGE_ARTICLE:UPDATE:TEAM','KNOWLEDGE_ARTICLE:UPDATE:DEPARTMENT','KNOWLEDGE_ARTICLE:UPDATE:ORGANIZATION')")
    public ApiResponse<KnowledgeArticleDto> publish(@PathVariable UUID articleId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(KnowledgeArticleDto.from(articleService.publish(principal, articleId)), "Article published");
    }

    @PostMapping("/{articleId}/archive")
    @PreAuthorize("hasAnyAuthority('KNOWLEDGE_ARTICLE:UPDATE:TEAM','KNOWLEDGE_ARTICLE:UPDATE:DEPARTMENT','KNOWLEDGE_ARTICLE:UPDATE:ORGANIZATION')")
    public ApiResponse<KnowledgeArticleDto> archive(@PathVariable UUID articleId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(KnowledgeArticleDto.from(articleService.archive(principal, articleId)), "Article archived");
    }

    @DeleteMapping("/{articleId}")
    @PreAuthorize("hasAnyAuthority('KNOWLEDGE_ARTICLE:DELETE:TEAM','KNOWLEDGE_ARTICLE:DELETE:DEPARTMENT','KNOWLEDGE_ARTICLE:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID articleId, @AuthenticationPrincipal UserPrincipal principal) {
        articleService.delete(principal, articleId);
        return ApiResponse.ok(null, "Article deleted");
    }
}
