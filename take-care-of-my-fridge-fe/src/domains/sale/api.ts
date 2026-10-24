import {
  adminSaleEventApi,
  type FlashDropCreateRequest,
  type PageResponseSaleCandidateProductResponse,
  type PageResponseSaleEventListResponse,
  type SaleCandidateProductsRequest,
  type SaleEventCreateResponse,
  type SaleEventListRequest,
  type SpecialSaleCreateRequest,
} from '@/common/api/clients';
import { unwrap } from '@/common/api/unwrap';

export async function createSpecialSale(
  request: SpecialSaleCreateRequest,
): Promise<SaleEventCreateResponse> {
  return unwrap(adminSaleEventApi.createSpecialSale({ specialSaleCreateRequest: request }));
}

export async function createFlashDrop(
  request: FlashDropCreateRequest,
): Promise<SaleEventCreateResponse> {
  return unwrap(adminSaleEventApi.createFlashDrop({ flashDropCreateRequest: request }));
}

// 행사에 넣을 수 있는 상품(판매 중, 재고 있음, 예정·진행 중인 행사에 들지 않음)만 온다.
export async function getSaleCandidateProducts(
  request: SaleCandidateProductsRequest,
  signal?: AbortSignal,
): Promise<PageResponseSaleCandidateProductResponse> {
  return unwrap(adminSaleEventApi.getCandidateProducts({ request }, { signal }));
}

export async function getSaleEvents(
  request: SaleEventListRequest,
  signal?: AbortSignal,
): Promise<PageResponseSaleEventListResponse> {
  return unwrap(adminSaleEventApi.getSaleEvents({ request }, { signal }));
}
