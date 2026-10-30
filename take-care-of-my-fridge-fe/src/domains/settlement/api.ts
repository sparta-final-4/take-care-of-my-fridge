import {
  adminSettlementApi,
  type PageResponseSettlementPaymentResponse,
  type SalesAmountResponse,
  type SettlementPaymentResponse,
  type SettlementPeriodResponse,
  type SettlementResponse,
  type SettlementSearchRequestUnitEnum,
  type TodaySalesResponse,
} from '@/common/api/clients';
import { unwrap } from '@/common/api/unwrap';

export type {
  PageResponseSettlementPaymentResponse,
  SalesAmountResponse,
  SettlementPaymentResponse,
  SettlementPeriodResponse,
  SettlementResponse,
  TodaySalesResponse,
};

export type SettlementUnit = SettlementSearchRequestUnitEnum;

// 시작일과 종료일(YYYY-MM-DD)을 모두 포함한다
export interface SettlementSearch {
  unit: SettlementUnit;
  startDate: string;
  endDate: string;
}

// 건별 조회. page는 1부터 센다
export interface SettlementPaymentSearch {
  startDate: string;
  endDate: string;
  page: number;
  size: number;
}

export async function getSettlement(
  search: SettlementSearch,
  signal?: AbortSignal,
): Promise<SettlementResponse> {
  return unwrap(adminSettlementApi.getSettlement({ request: search }, { signal }));
}

export async function getSettlementPayments(
  search: SettlementPaymentSearch,
  signal?: AbortSignal,
): Promise<PageResponseSettlementPaymentResponse> {
  return unwrap(adminSettlementApi.getPayments({ request: search }, { signal }));
}

export async function getTodaySales(signal?: AbortSignal): Promise<TodaySalesResponse> {
  return unwrap(adminSettlementApi.getTodaySales({ signal }));
}
