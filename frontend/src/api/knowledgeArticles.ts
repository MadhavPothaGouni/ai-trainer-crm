import { apiClient, unwrap } from "../lib/apiClient";
import type { CreateKnowledgeArticleRequest, KnowledgeArticleDto, PageResponse, UpdateKnowledgeArticleRequest } from "../types/api";

export interface ListKnowledgeArticlesParams {
  page?: number;
  size?: number;
  sort?: string;
  category?: string;
}

export function listKnowledgeArticles(params: ListKnowledgeArticlesParams = {}): Promise<PageResponse<KnowledgeArticleDto>> {
  return unwrap(apiClient.get("/api/v1/knowledge-articles", { params }));
}

export function getKnowledgeArticle(articleId: string): Promise<KnowledgeArticleDto> {
  return unwrap(apiClient.get(`/api/v1/knowledge-articles/${articleId}`));
}

export function createKnowledgeArticle(request: CreateKnowledgeArticleRequest): Promise<KnowledgeArticleDto> {
  return unwrap(apiClient.post("/api/v1/knowledge-articles", request));
}

export function updateKnowledgeArticle(articleId: string, request: UpdateKnowledgeArticleRequest): Promise<KnowledgeArticleDto> {
  return unwrap(apiClient.put(`/api/v1/knowledge-articles/${articleId}`, request));
}

export function publishKnowledgeArticle(articleId: string): Promise<KnowledgeArticleDto> {
  return unwrap(apiClient.post(`/api/v1/knowledge-articles/${articleId}/publish`));
}

export function archiveKnowledgeArticle(articleId: string): Promise<KnowledgeArticleDto> {
  return unwrap(apiClient.post(`/api/v1/knowledge-articles/${articleId}/archive`));
}

export function deleteKnowledgeArticle(articleId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/knowledge-articles/${articleId}`));
}

/** KNOWLEDGE_ARTICLE:EXPORT - downloads a CSV of every article in the org. Same bypass-unwrap-and-trigger-a-download shape as api/campaigns.ts#exportCampaignsCsv. */
export async function exportKnowledgeArticlesCsv(): Promise<void> {
  const response = await apiClient.get("/api/v1/knowledge-articles/export", { responseType: "blob" });
  const url = URL.createObjectURL(response.data as Blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = "knowledge-articles.csv";
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}
