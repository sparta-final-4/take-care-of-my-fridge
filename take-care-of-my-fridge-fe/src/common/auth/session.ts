import { flushSync } from 'react-dom';

import { queryClient } from '@/app/queryClient';
import type { MyInfoResponse } from '@/common/api/clients';
import { clearRecentKeywords } from '@/domains/search/recentKeywords';

import { useAuthStore } from './authStore';
import { nextSessionId, sessionId } from './sessionId';

export async function setSession(
  member: MyInfoResponse | null,
  reason: 'expired' | 'suspended' | null = null,
) {
  const id = nextSessionId();
  // 로그아웃·만료 뒤 같은 브라우저를 쓰는 사람에게 이전 회원의 최근 검색어가 보이지 않게 한다.
  if (!member && useAuthStore.getState().status === 'authenticated') clearRecentKeywords();
  await queryClient.cancelQueries();
  if (id !== sessionId()) return;
  // 화면이 붙어 있는 쿼리는 캐시에서 빼면 다시 받지 못해 로딩에 멈춘다.
  // type: 'inactive'는 enabled가 꺼진 화면 쿼리도 포함하므로 observer 수로 고른다.
  queryClient.removeQueries({ predicate: (query) => query.getObserversCount() === 0 });
  // 새 로그인 상태로 먼저 다시 그려 enabled·화면 전환을 반영한 뒤, 남은 쿼리를 비우고 다시 받는다.
  // 재요청은 기다리지 않는다. 기다리면 호출부의 화면 이동과 인터셉터의 reject가 그만큼 늦어진다.
  flushSync(() => {
    useAuthStore.setState({ status: member ? 'authenticated' : 'anonymous', member, reason });
  });
  void queryClient.resetQueries();
}
