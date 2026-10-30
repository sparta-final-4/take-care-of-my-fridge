import { adminForecastApi, type ForecastResponse } from '@/common/api/clients';
import { unwrap } from '@/common/api/unwrap';

export async function getForecast(
  weekStart?: string,
  signal?: AbortSignal,
): Promise<ForecastResponse> {
  return unwrap(
    weekStart
      ? adminForecastApi.getByWeek({ weekStart }, { signal })
      : adminForecastApi.getLatest({ signal }),
  );
}
