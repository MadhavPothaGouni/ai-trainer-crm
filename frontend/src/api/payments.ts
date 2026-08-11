import { apiClient, unwrap } from "../lib/apiClient";
import type { CreatePaymentRequest, PageResponse, PaymentDto } from "../types/api";

export interface ListPaymentsParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listPayments(invoiceId: string, params: ListPaymentsParams = {}): Promise<PageResponse<PaymentDto>> {
  return unwrap(apiClient.get(`/api/v1/invoices/${invoiceId}/payments`, { params }));
}

export function recordPayment(invoiceId: string, request: CreatePaymentRequest): Promise<PaymentDto> {
  return unwrap(apiClient.post(`/api/v1/invoices/${invoiceId}/payments`, request));
}

export function getPayment(paymentId: string): Promise<PaymentDto> {
  return unwrap(apiClient.get(`/api/v1/payments/${paymentId}`));
}

export function deletePayment(paymentId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/payments/${paymentId}`));
}
