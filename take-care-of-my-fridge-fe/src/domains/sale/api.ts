import {
  adminSaleEventApi,
  type FlashDropCreateRequest,
  type PageResponseSaleCandidateProductResponse,
  type PageResponseSaleEventListResponse,
  type PopularSaleResponse,
  saleApi,
  type SaleCandidateProductsRequest,
  type SaleEventDetailResponse,
  type SaleEventListRequest,
  type SaleEventsCreateResponse,
  type SaleEventUpdateRequest,
  type SpecialSaleCreateRequest,
} from '@/common/api/clients';
import { unwrap } from '@/common/api/unwrap';

export async function createSpecialSale(
  request: SpecialSaleCreateRequest,
): Promise<SaleEventsCreateResponse> {
  return unwrap(adminSaleEventApi.createSpecialSale({ specialSaleCreateRequest: request }));
}

export async function createFlashDrop(
  request: FlashDropCreateRequest,
): Promise<SaleEventsCreateResponse> {
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

// 수정 화면에 보여 줄 행사 하나와 그 행사 항목들
export async function getSaleEvent(
  saleEventId: number,
  signal?: AbortSignal,
): Promise<SaleEventDetailResponse> {
  return unwrap(adminSaleEventApi.getSaleEvent({ saleEventId }, { signal }));
}

// 시작 전인 행사의 시각과 행사 항목 값을 한 번에 고친다.
export async function updateSaleEvent(
  saleEventId: number,
  request: SaleEventUpdateRequest,
): Promise<void> {
  await unwrap(adminSaleEventApi.updateSaleEvent({ saleEventId, saleEventUpdateRequest: request }));
}

// 진행 중인 할인 행사를 지금 닫는다. 종료 시각은 바뀌지 않는다.
export async function endSaleEvent(saleEventId: number): Promise<void> {
  await unwrap(adminSaleEventApi.endSaleEvent({ saleEventId }));
}

// 시작 전인 할인 행사를 행사 항목까지 지운다.
export async function deleteSaleEvent(saleEventId: number): Promise<void> {
  await unwrap(adminSaleEventApi.deleteSaleEvent({ saleEventId }));
}

// 메인 화면 특가. 최근 검색 클릭이 많은 순으로 최대 6개이고, 지금 살 수 없는 상품은 서버가 뺀다.
export async function getPopularSales(signal?: AbortSignal): Promise<PopularSaleResponse[]> {
  return unwrap(saleApi.getPopularSales({ signal }));
}
