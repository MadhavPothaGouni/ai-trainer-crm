import { apiClient, unwrap } from "../lib/apiClient";
import type {
  CreateInvoiceLineItemRequest,
  GenerateInvoiceRequest,
  InvoiceDto,
  InvoiceLineItemDto,
  PageResponse,
  UpdateInvoiceLineItemRequest,
  UpdateInvoiceRequest,
} from "../types/api";

export interface ListInvoicesParams {
  page?: number;
  size?: number;
  sort?: string;
  orderId?: string;
}

export function listInvoices(params: ListInvoicesParams = {}): Promise<PageResponse<InvoiceDto>> {
  return unwrap(apiClient.get("/api/v1/invoices", { params }));
}

export function getInvoice(invoiceId: string): Promise<InvoiceDto> {
  return unwrap(apiClient.get(`/api/v1/invoices/${invoiceId}`));
}

export function generateInvoiceFromOrder(orderId: string, request: GenerateInvoiceRequest): Promise<InvoiceDto> {
  return unwrap(apiClient.post(`/api/v1/invoices/from-order/${orderId}`, request));
}

export function updateInvoice(invoiceId: string, request: UpdateInvoiceRequest): Promise<InvoiceDto> {
  return unwrap(apiClient.put(`/api/v1/invoices/${invoiceId}`, request));
}

export function issueInvoice(invoiceId: string): Promise<InvoiceDto> {
  return unwrap(apiClient.post(`/api/v1/invoices/${invoiceId}/issue`));
}

export function voidInvoice(invoiceId: string): Promise<InvoiceDto> {
  return unwrap(apiClient.post(`/api/v1/invoices/${invoiceId}/void`));
}

export function deleteInvoice(invoiceId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/invoices/${invoiceId}`));
}

export function addInvoiceLineItem(invoiceId: string, request: CreateInvoiceLineItemRequest): Promise<InvoiceLineItemDto> {
  return unwrap(apiClient.post(`/api/v1/invoices/${invoiceId}/line-items`, request));
}

export function updateInvoiceLineItem(
  invoiceId: string,
  lineItemId: string,
  request: UpdateInvoiceLineItemRequest,
): Promise<InvoiceLineItemDto> {
  return unwrap(apiClient.put(`/api/v1/invoices/${invoiceId}/line-items/${lineItemId}`, request));
}

export function removeInvoiceLineItem(invoiceId: string, lineItemId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/invoices/${invoiceId}/line-items/${lineItemId}`));
}
