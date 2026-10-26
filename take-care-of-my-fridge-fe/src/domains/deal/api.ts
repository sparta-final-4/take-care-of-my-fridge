import {
  adminDealAnalysisApi,
  type DealAnalysisResponse,
  type DealEventRequest,
  type DealEventResponse,
  type DealReviewRequest,
} from '@/common/api/clients';
import { unwrap } from '@/common/api/unwrap';

export async function getLatestDealAnalysis(signal?: AbortSignal): Promise<DealAnalysisResponse> {
  return unwrap(adminDealAnalysisApi.getLatest1({ signal }));
}

export async function saveDealReview(
  analysisId: number,
  request: DealReviewRequest,
): Promise<void> {
  await unwrap(adminDealAnalysisApi.saveReviews({ analysisId, dealReviewRequest: request }));
}

export async function registerDealEvent(
  analysisId: number,
  request: DealEventRequest,
): Promise<DealEventResponse> {
  return unwrap(adminDealAnalysisApi.registerEvent({ analysisId, dealEventRequest: request }));
}
