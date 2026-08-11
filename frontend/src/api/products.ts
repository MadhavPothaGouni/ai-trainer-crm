import { apiClient, unwrap } from "../lib/apiClient";
import type { CreateProductRequest, PageResponse, ProductDto, UpdateProductRequest } from "../types/api";

export interface ListProductsParams {
  page?: number;
  size?: number;
  sort?: string;
}

export function listProducts(params: ListProductsParams = {}): Promise<PageResponse<ProductDto>> {
  return unwrap(apiClient.get("/api/v1/products", { params }));
}

export function getProduct(productId: string): Promise<ProductDto> {
  return unwrap(apiClient.get(`/api/v1/products/${productId}`));
}

export function createProduct(request: CreateProductRequest): Promise<ProductDto> {
  return unwrap(apiClient.post("/api/v1/products", request));
}

export function updateProduct(productId: string, request: UpdateProductRequest): Promise<ProductDto> {
  return unwrap(apiClient.put(`/api/v1/products/${productId}`, request));
}

export function deleteProduct(productId: string): Promise<null> {
  return unwrap(apiClient.delete(`/api/v1/products/${productId}`));
}
