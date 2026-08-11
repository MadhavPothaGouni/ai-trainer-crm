import { apiClient, unwrap } from "../lib/apiClient";
import type {
  AssignOwnerRequest,
  CreateQuoteLineItemRequest,
  CreateQuoteRequest,
  PageResponse,
  QuoteDto,
  QuoteLineItemDto,
  UpdateQuoteLineItemRequest,
  UpdateQuoteRequest,
  UpdateQuoteStatusRequest,
} from "../types/api";

export interface ListQuotesParams {
  page?: number;
  size?: number;
  sort?: string;
  opportunityId?: string;
}

export function listQuotes(params: ListQuotesParams = {}): Promise<PageResponse<QuoteDto>> {
  return unwrap(apiClient.get("/api/v1/quotes", { params }));
}

export function getQuote(quoteId: string): Promise<QuoteDto> {
  return unwrap(apiClient.get(`/api/v1/quotes/${quoteId}`));
}

export function createQuote(request: CreateQuoteRequest): Promise<QuoteDto> {
  return unwrap(apiClient.post("/api/v1/quotes", request));
}

export function updateQuote(quoteId: string, request: UpdateQuoteRequest): Promise<QuoteDto> {
  return unwrap(apiClient.put(`/api/v1/quotes/${quoteId}`, request));
}

export function updateQuoteStatus(quoteId: string, request: UpdateQuoteStatusRequest): Promise<QuoteDto> {
  return unwrap(apiClient.patch(`/api/v1/quotes/${quoteId}/status`, request));
}

export function deleteQuote(quoteId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/quotes/${quoteId}`));
}

export function assignQuoteOwner(quoteId: string, request: AssignOwnerRequest): Promise<QuoteDto> {
  return unwrap(apiClient.patch(`/api/v1/quotes/${quoteId}/owner`, request));
}

export function addQuoteLineItem(quoteId: string, request: CreateQuoteLineItemRequest): Promise<QuoteLineItemDto> {
  return unwrap(apiClient.post(`/api/v1/quotes/${quoteId}/line-items`, request));
}

export function updateQuoteLineItem(
  quoteId: string,
  lineItemId: string,
  request: UpdateQuoteLineItemRequest,
): Promise<QuoteLineItemDto> {
  return unwrap(apiClient.put(`/api/v1/quotes/${quoteId}/line-items/${lineItemId}`, request));
}

export function removeQuoteLineItem(quoteId: string, lineItemId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/quotes/${quoteId}/line-items/${lineItemId}`));
}
