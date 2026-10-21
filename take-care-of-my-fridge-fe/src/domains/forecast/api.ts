import { axiosInstance } from '@/common/api/axiosInstance';
import type { ApiResponse } from '@/common/api/types';

export interface ForecastItem {
  productId: number;
  productName: string;
  predictedRank: number | null;
  predictedQty: number | null;
  baselineQty: number;
  actualRank: number | null;
  actualQty: number | null;
  rankChange: number | null;
  qtyError: number | null;
  status: 'MATCHED' | 'DROPPED' | 'ENTERED' | null;
}

export interface ForecastResponse {
  weekStart: string;
  weekEnd: string;
  modelVersion: string;
  dataCutoffAt: string;
  createdAt: string;
  confirmed: boolean;
  confirmedAt: string | null;
  syntheticData: boolean;
  previousWeekStart: string | null;
  nextWeekStart: string | null;
  summary: {
    top10MatchCount: number;
    baselineTop10MatchCount: number;
    meanAbsoluteError: number;
    baselineMeanAbsoluteError: number;
    enteredCount: number;
    droppedCount: number;
  } | null;
  items: ForecastItem[];
}

export async function getForecast(weekStart?: string, signal?: AbortSignal) {
  const path = weekStart
    ? `/api/admin/forecasts/${encodeURIComponent(weekStart)}`
    : '/api/admin/forecasts/latest';
  const { data } = await axiosInstance.get<ApiResponse<ForecastResponse>>(path, { signal });
  return data.data;
}
