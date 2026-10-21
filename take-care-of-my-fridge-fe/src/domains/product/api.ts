import {
  productApi,
  type PageResponseProductListItemResponse,
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
