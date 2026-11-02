# WORK-135: 상담 바뀜 신호 (알림 SSE·Redis Pub/Sub)

상태: 검증 대기 · 작성일: 2026-11-02 · 담당: zcookiez

## 목표와 완료 기준

- 여러 창(브라우저·기기·서버)에서 AI 상담이 맞춰지도록, 상담이 바뀌면 그 회원의 알림 SSE 연결로 `event: chat` 신호를 보낸다.
- 커밋된 변경만 보내고 롤백되면 보내지 않는다. 트랜잭션이 없는 상담 시작 경로에서도 보낸다.
- 서버가 여러 대여도 신호가 그 회원의 연결이 있는 서버에 닿는다.

## 근거와 범위

- 선행 작업: WORK-130 알림 SSE, WORK-134 같은 상담 동시 답변 잠금(1단계).
- 여러 창 동기화의 2단계. 이번은 백엔드만. 프론트 수신(`useNotificationStream`에서 `chat` 이벤트 처리)은 범위 밖.
- 알림 코드와 `global/sse/SseConnectionRegistry`는 바꾸지 않는다. 채널을 알림과 따로 둔다(A안).

## AI 활용과 개발자 판단

- 맡긴 작업: 조사 결과대로 이벤트·커밋 뒤 리스너·Redis 발행/구독·문서·테스트 구현.
- 결정: 리스너는 `@TransactionalEventListener(AFTER_COMMIT, fallbackExecution = true)`. 상담 시작(`getOrCreateActiveSession`)은 `NOT_SUPPORTED`라 트랜잭션이 없고, 그 안의 `expireSession`도 자기 호출이라 트랜잭션 없이 실행된다. 이때는 저장이 이미 끝난 뒤라 바로 보내도 순서가 맞다.
- `completeSessionFromCooking`은 실제로 종료했을 때만 발행한다(이미 만료된 상담은 상태가 안 바뀌므로 신호 없음).
- SSE data는 회원 번호를 뺀 record `ChatSessionPushSignal`을 JSON 문자열로 만들어 보낸다. 필드 순서를 고정하고 문자열로 보내 연결의 메시지 변환기에 맡기지 않는다.

## 구현 결과

- 이벤트 `domain/chat/event/ChatSessionChangedEvent(memberId, sessionId, status)`. 발행 지점(ApplicationEventPublisher):
  - `ChatMessageService.saveExchange`: 답 저장 뒤 `ACTIVE`
  - `ChatSessionService.completeSession`: `COMPLETED`
  - `ChatSessionService.completeSessionFromCooking`: 종료했을 때만 `COMPLETED`(요리 완료 트랜잭션 커밋 뒤 전송)
  - `ChatSessionService.expireSession`: 실제로 만료시켰을 때만 `EXPIRED`
  - `ChatSessionService.getOrCreateActiveSession`: 새로 만들었을 때만 `ACTIVE`(기존 진행 중 상담 반환, 저장 경합으로 다른 요청이 만든 상담 반환 때는 없음)
- 리스너 `ChatSessionChangedEventListener`: 커밋 뒤 `ChatSessionPushPublisher`를 부른다.
- `domain/chat/push`: `ChatSessionPushMessage`(Redis 메시지 `{"memberId","sessionId","status"}`), `ChatSessionPushPublisher`(채널 `chat:push`, 실패는 로그만), `ChatSessionPushSubscriber`(알림 구독기와 같게 지연 구독·재시도·정지 때 해제, 받으면 `SseConnectionRegistry.send(memberId, "chat", "{\"sessionId\":..,\"status\":..}")`), `ChatSessionPushSignal`(SSE data).
- `RedisConfig` 주석 키 규칙에 Pub/Sub 채널 `notification:push`, `chat:push` 추가.
- 문서: `docs/api/api-spec.md` 알림 스트림에 `chat` 이벤트·data 형식·보내는 때 추가.
- API 엔드포인트·DTO·ErrorCode·DB 변경 없음.

## 검증

| 명령·시나리오 | 실행 환경·조건 | 통과/실패/미실행 | 근거 |
|---|---|---|---|
| `./gradlew clean test` | 로컬, Testcontainers(MySQL·Redis) | 통과 | 218개 묶음, 1315건, 실패·오류 0 |
| `ChatSessionServiceTest`(+7)·`ChatMessageServiceTest`(+1) | 단위 | 통과 | 발행 지점 5곳, 만료는 실제 만료 때만, 시작은 새로 만들 때만, 실패 때 발행 없음 |
| `ChatSessionChangedEventTransactionTest` | 실제 MySQL, 발행기 mock | 통과 (8건) | 커밋 뒤에만 발행, 롤백 때 없음, 바깥 트랜잭션 커밋 전 없음, 트랜잭션 없는 시작 경로, 만료→새 상담 순서, 보내기 검증 만료 |
| `ChatSessionPushRedisTest` | 실제 Redis, 서버 두 대 몫 구독기 | 통과 (4건) | 두 서버 모두 수신, 그 회원 번호로만 send, 알림 채널과 안 섞임, 깨진 메시지 뒤에도 구독 유지 |
| `ChatSessionPushSubscriberTest`·`ChatSessionPushPublisherTest` | 단위 | 통과 (7·2건) | send(memberId, "chat", JSON), 구독 수명, 발행 실패 시 예외 없음 |
| 브라우저 두 창에서 실제 신호 수신 | 브라우저 | 미실행 | 프론트 수신 미구현 |

## 연결과 남은 작업

- 실제 커밋·PR: 아직 없음
- 다음 단계: 프론트가 `chat` 이벤트를 받아 상담·메시지 기록을 다시 받기(같은 창에서 답을 받는 중인 상담은 `useChatStreamingStore`로 건너뛰기).
- 미검증: 실제 다중 서버 배포 환경, 프론트 수신. 서버 한 대 안에서 연결별 실제 전달은 `SseConnectionRegistry`가 맡고 그 테스트로 확인되어 있으며, 이번 테스트는 서버별 `send` 호출까지 확인한다.
