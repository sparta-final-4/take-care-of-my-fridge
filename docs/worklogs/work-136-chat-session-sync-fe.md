# WORK-136: 프론트 상담 바뀜 신호 수신 (여러 창 상담 맞추기)

상태: 검증 대기 · 작성일: 2026-11-02 · 담당: zcookiez

## 목표와 완료 기준

- 알림 SSE의 `event: chat` 신호를 받아 다른 창에서 바뀐 상담 목록·대화를 바로 맞춘다.
- 알림 SSE 연결을 공통으로 옮기되 알림 동작은 그대로 둔다.
- 진행 중 상담 쿼리(POST)를 함부로 다시 받지 않아 새 빈 상담이 저절로 생기지 않는다.

## 근거와 범위

- 선행 작업: WORK-130 알림 SSE(준혁), WORK-134 동시 답변 잠금, WORK-135 상담 바뀜 신호(백엔드).
- 여러 창 동기화의 3단계(마지막). 연결 공통화는 준혁님과 정한 방향.

## AI 활용과 개발자 판단

- 연결은 `common/sse/useMemberEventStream`이 열고, 도메인은 `{ event, onMessage, onReconnect }` 처리기만 낸다. `common`이 도메인을 import하지 않도록 처리기 묶음은 헤더(`layout/Header.tsx`)에서 모듈 상수로 넘긴다(바뀌면 다시 연결되므로).
- 여는 조건은 기존과 같게 `로그인 && 관리자 아님`. 기존에는 헤더가 일반 회원에게만 그리는 종 컴포넌트 안에서 열었다.
- 진행 중 상담은 끝남 신호면 `setQueryData`로 상태만 바꾸고, 다른 창의 새 상담 시작일 때만 무효화한다. 규칙 8.2에 예외로 추가했다.

## 구현 결과

- `common/sse/useMemberEventStream.ts`(새로 만듦): `openMemberEventStream`(EventSource 열기, 이벤트별 처리기 연결, 처음이 아닌 연결 때 처리기마다 다시 받기)과 훅.
- `domains/notification/notificationEvent.ts`(새로 만듦): 기존 `useNotificationStream`의 처리(알림 쿼리 전체 무효화, 다시 연결 때도 같음)를 처리기로 옮김. `useNotificationStream.ts`는 지우고 `NotificationBell`의 호출을 뺐다.
- `domains/chat/chatSessionEvent.ts`(새로 만듦): `chat` 처리기.
  - 상담 목록 무효화, 그 상담의 메시지 기록 무효화(이 창이 그 상담 답을 받는 중이면 건너뜀)
  - 진행 중 상담: COMPLETED·EXPIRED이고 캐시 id와 같으면 상태만 바꿈, ACTIVE이고 캐시 id와 다르면 그때만 무효화, 캐시가 없으면 아무것도 안 함
  - 다시 연결 때 상담 목록과, 지금 열려 있는 대화(관찰자가 있는 메시지 기록 쿼리: 채팅창·상담 내역 오른쪽)를 다시 받음. 답 받는 중인 상담은 건너뜀
- `layout/Header.tsx`: 연결 호출과 처리기 묶음.
- 규칙: `take-care-of-my-fridge-fe/CLAUDE.md` 8.2에 "서버 신호로 상태만 맞출 때" 예외 추가.

## 검증

| 명령·시나리오 | 실행 환경·조건 | 통과/실패/미실행 | 근거 |
|---|---|---|---|
| `pnpm lint`·`typecheck`·`build`·`test` | 로컬 | 통과 | 테스트 152건 |
| `prettier --check`(바꾼 파일) | 로컬 | 통과 | 전체 `format:check`는 기존 CRLF 문제로 실패하던 상태 |
| `useMemberEventStream.test.ts` | 가짜 EventSource·가짜 처리기 | 통과 (3건) | URL·쿠키·닫기, 이벤트 이름별 처리기 연결, 처음이 아닌 연결 때만 다시 받기 |
| `notificationEvent.test.ts` | 실제 QueryClient | 통과 (3건) | 기존 알림 동작 그대로: notification 이벤트, 신호·다시 연결 때 알림 쿼리 전체만 |
| `chatSessionEvent.test.ts` | 실제 QueryClient | 통과 (14건) | 무효화 대상, 받는 중 건너뛰기, 상태만 바꾸기, 새 상담 때만 무효화, 캐시 없을 때, 다시 연결 때 열린 대화만·받는 중 제외·진행 중 상담 제외 |
| 브라우저 두 개로 메시지·종료·새 상담·받는 중 확인, 알림 종 갱신, DB `chat_sessions` 확인 | 브라우저 | 미실행 | 없음 |

## 연결과 남은 작업

- 실제 커밋·PR: 아직 없음
- 미검증: 위 브라우저 시나리오 전부, 375/768/1440 화면.
- 남은 경합: 다른 창의 새 상담 ACTIVE 신호로 진행 중 상담을 다시 받는(POST) 사이에 그 상담이 이미 끝났다면 서버가 새 상담을 만든다. 신호 두 개가 아주 짧은 간격으로 올 때만 생긴다.
