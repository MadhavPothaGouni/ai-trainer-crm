import { apiClient, unwrap } from "../lib/apiClient";
import type {
  CreatePurchaseOrderRequest,
  PageResponse,
  PurchaseOrderDto,
  UpdatePurchaseOrderRequest,
  UpdatePurchaseOrderStatusRequest,
} from "../types/api";

export interface ListPurchaseOrdersParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listPurchaseOrders(params: ListPurchaseOrdersParams = {}): Promise<PageResponse<PurchaseOrderDto>> {
  return unwrap(apiClient.get("/api/v1/purchase-orders", { params }));
}

export function getPurchaseOrder(purchaseOrderId: string): Promise<PurchaseOrderDto> {
  return unwrap(apiClient.get(`/api/v1/purchase-orders/${purchaseOrderId}`));
}

export function createPurchaseOrder(request: CreatePurchaseOrderRequest): Promise<PurchaseOrderDto> {
  return unwrap(apiClient.post("/api/v1/purchase-orders", request));
}

export function updatePurchaseOrder(purchaseOrderId: string, request: UpdatePurchaseOrderRequest): Promise<PurchaseOrderDto> {
  return unwrap(apiClient.put(`/api/v1/purchase-orders/${purchaseOrderId}`, request));
}

export function updatePurchaseOrderStatus(purchaseOrderId: string, request: UpdatePurchaseOrderStatusRequest): Promise<PurchaseOrderDto> {
  return unwrap(apiClient.patch(`/api/v1/purchase-orders/${purchaseOrderId}/status`, request));
}

export function deletePurchaseOrder(purchaseOrderId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/purchase-orders/${purchaseOrderId}`));
}
