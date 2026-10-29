import {
  adminProductApi,
  productApi,
  type AdminProductListRequest,
  type PageResponseProductListItemResponse,
  type ProductCreateRequest,
  type ProductDetailResponse,
  type ProductListRequest,
} from '@/common/api/clients';
import { unwrap } from '@/common/api/unwrap';

export async function getProducts(
  request: ProductListRequest,
  signal?: AbortSignal,
): Promise<PageResponseProductListItemResponse> {
  return unwrap(productApi.getProducts({ request }, { signal }));
}

export async function getProduct(
  productId: number,
  signal?: AbortSignal,
): Promise<ProductDetailResponse> {
  return unwrap(productApi.getProduct({ productId }, { signal }));
}

export async function createProduct(request: ProductCreateRequest): Promise<ProductDetailResponse> {
  return unwrap(adminProductApi.createProduct({ productCreateRequest: request }));
}

export async function getAdminProducts(
  request: AdminProductListRequest,
  signal?: AbortSignal,
): Promise<PageResponseProductListItemResponse> {
  return unwrap(adminProductApi.getAdminProducts({ request }, { signal }));
}

export async function deleteProduct(productId: number): Promise<void> {
  await unwrap(adminProductApi.deleteProduct({ productId }));
}

export async function getAdminProduct(
  productId: number,
  signal?: AbortSignal,
): Promise<ProductDetailResponse> {
  return unwrap(adminProductApi.getAdminProduct({ productId }, { signal }));
}
