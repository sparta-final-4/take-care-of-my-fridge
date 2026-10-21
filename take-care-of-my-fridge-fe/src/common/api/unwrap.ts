import type { AxiosResponse } from 'axios';

// 생성 클라이언트 호출(Promise<AxiosResponse<ApiResponseXxx>>)을 받아 data만 꺼낸다.
// 실패는 HTTP 오류 상태로 와서 axios가 throw하므로, 여기까지 온 응답은 성공이다.
export async function unwrap<T>(request: Promise<AxiosResponse<{ data?: T }>>) {
  const { data } = await request;
  return data.data as T;
}
