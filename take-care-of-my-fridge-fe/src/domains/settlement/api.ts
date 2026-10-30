import {
  adminSettlementApi,
  type SalesAmountResponse,
  type SettlementPeriodResponse,
  type SettlementResponse,
  type SettlementSearchRequestUnitEnum,
  type TodaySalesResponse,
} from '@/common/api/clients';
import { unwrap } from '@/common/api/unwrap';

export type {
  SalesAmountResponse,
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

export async function getSettlement(
  search: SettlementSearch,
  signal?: AbortSignal,
): Promise<SettlementResponse> {
  return unwrap(adminSettlementApi.getSettlement({ request: search }, { signal }));
}

export async function getTodaySales(signal?: AbortSignal): Promise<TodaySalesResponse> {
  return unwrap(adminSettlementApi.getTodaySales({ signal }));
}
