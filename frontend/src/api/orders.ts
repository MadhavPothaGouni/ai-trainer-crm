import { apiClient, unwrap } from "../lib/apiClient";
import type {
  CreateOrderFromQuoteRequest,
  CreateOrderLineItemRequest,
  CreateOrderRequest,
  OrderDto,
  OrderLineItemDto,
  PageResponse,
  UpdateOrderLineItemRequest,
  UpdateOrderRequest,
  UpdateOrderStatusRequest,
} from "../types/api";

export interface ListOrdersParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listOrders(params: ListOrdersParams = {}): Promise<PageResponse<OrderDto>> {
  return unwrap(apiClient.get("/api/v1/orders", { params }));
}

export function getOrder(orderId: string): Promise<OrderDto> {
  return unwrap(apiClient.get(`/api/v1/orders/${orderId}`));
}

export function createOrder(request: CreateOrderRequest): Promise<OrderDto> {
  return unwrap(apiClient.post("/api/v1/orders", request));
}

export function createOrderFromQuote(quoteId: string, request: CreateOrderFromQuoteRequest): Promise<OrderDto> {
  return unwrap(apiClient.post(`/api/v1/orders/from-quote/${quoteId}`, request));
}

export function updateOrder(orderId: string, request: UpdateOrderRequest): Promise<OrderDto> {
  return unwrap(apiClient.put(`/api/v1/orders/${orderId}`, request));
}

export function confirmOrder(orderId: string): Promise<OrderDto> {
  return unwrap(apiClient.post(`/api/v1/orders/${orderId}/confirm`));
}

export function updateOrderStatus(orderId: string, request: UpdateOrderStatusRequest): Promise<OrderDto> {
  return unwrap(apiClient.patch(`/api/v1/orders/${orderId}/status`, request));
}

export function deleteOrder(orderId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/orders/${orderId}`));
}

export function addOrderLineItem(orderId: string, request: CreateOrderLineItemRequest): Promise<OrderLineItemDto> {
  return unwrap(apiClient.post(`/api/v1/orders/${orderId}/line-items`, request));
}

export function updateOrderLineItem(
  orderId: string,
  lineItemId: string,
  request: UpdateOrderLineItemRequest,
): Promise<OrderLineItemDto> {
  return unwrap(apiClient.put(`/api/v1/orders/${orderId}/line-items/${lineItemId}`, request));
}

export function removeOrderLineItem(orderId: string, lineItemId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/orders/${orderId}/line-items/${lineItemId}`));
}
