import {
  adminImageApi,
  adminProductApi,
  productApi,
  type AdminProductListRequest,
  type PageResponseProductListItemResponse,
  type ProductCreateRequest,
  type ProductDetailResponse,
  type ProductListRequest,
  type ProductUpdateRequest,
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

export async function updateProduct(
  productId: number,
  request: ProductUpdateRequest,
): Promise<ProductDetailResponse> {
  return unwrap(adminProductApi.updateProduct({ productId, productUpdateRequest: request }));
}

// 상품 이미지를 S3에 직접 올리고, 상품 imageUrl에 저장할 조회 URL을 돌려준다.
// S3 PUT은 Presigned URL로 보내므로 axiosInstance(쿠키·CSRF 헤더)를 쓰지 않고 fetch로 보낸다.
// 서명에 Content-Type·Content-Length가 들어 있어, 발급 요청과 같은 파일·형식으로 보내야 한다.
export async function uploadProductImage(file: File): Promise<string> {
  const { uploadUrl, imageUrl } = await unwrap(
    adminImageApi.createProductImageUploadUrl({
      imageUploadUrlRequest: { contentType: file.type, fileSize: file.size },
    }),
  );
  if (!uploadUrl || !imageUrl) throw new Error('image upload url missing');

  const response = await fetch(uploadUrl, {
    method: 'PUT',
    headers: { 'Content-Type': file.type },
    body: file,
  });
  if (!response.ok) throw new Error(`image upload failed: ${response.status}`);
  return imageUrl;
}
