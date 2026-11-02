# WORK-134: 같은 상담 동시 답변 잠금

상태: 검증 대기 · 작성일: 2026-11-02 · 담당: zcookiez

## 목표와 완료 기준

- 여러 창(브라우저·기기·서버)에서 같은 상담에 동시에 보내면 둘 다 답을 받고, 저장 때 sequence 유니크 위반으로 뒤 요청이 실패하면서 하루 횟수만 쓰던 문제를 막는다.
- 같은 상담에 동시에 두 번 보내면 하나만 답을 받고 다른 하나는 409, 하루 횟수는 1만 늘어난다.
- 답이 완료·오류·끊김 중 어느 쪽으로 끝나도 잠금이 풀려 다시 보낼 수 있다.

## 근거와 범위

- 선행 작업: WORK-131 마이페이지 AI 상담 내역(채팅창·마이페이지 오른쪽 칸 실시간 대화), WORK-130 알림 SSE.
- 여러 창 동기화(알림 SSE의 Redis Pub/Sub에 "상담 바뀜" 신호 얹기)의 1단계. 2단계(신호 발행·수신)는 범위 밖.
- 변경 영역: 백엔드 chat 도메인·공통 오류 코드·Redis 설정 주석, 프론트 chat 도메인·공통 오류 문구.
- 가정: TTL 150초는 요청 타임아웃 120초 + 여유. 실제 AI 연결 때 다시 정한다.

## AI 활용과 개발자 판단

- 맡긴 작업: 조사 결과대로 잠금 서비스·순서·오류 코드·테스트와 프론트 스트리밍 기록·안내 문구 구현.
- 결정: 백엔드 규칙 4.6은 분산 락을 facade에서 Redisson으로 잡으라고 하지만, 이 잠금은 트랜잭션이 아니라 스트림 수명에 묶여 있어(요청 스레드가 끝난 뒤 doFinally에서 풀림) 사용자 지정대로 서비스 안 SET NX로 뒀다.
- 프론트 스토어: 채팅창 열림과 관심사가 달라 `useChatModalStore`를 넓히지 않고 작은 스토어를 새로 만들었다.

## 구현 결과

- 백엔드 `ChatStreamLockService`(새로 만듦): `chat:streaming:{sessionId}`에 요청마다 UUID 토큰을 SET NX EX로 건다(`app.chat.reply-lock-ttl`, 기본 150초, `ChatProperties.replyLockTtl`). 해제는 토큰이 같을 때만 지우는 Lua 스크립트. 획득 중 Redis 장애는 503 `CHAT_008`(하루 횟수와 같게), 해제 실패는 로그만 남기고 TTL에 맡긴다.
- `ChatStreamService.streamMessage` 순서: 세션 검증 → 잠금(실패 409 `CHAT_009`, 하루 횟수 안 씀) → 하루 횟수(실패 시 잠금 해제 후 던짐) → 스트림. 반환 Flux 맨 끝 `doFinally`에서 해제해 완료·오류·끊김을 모두 덮는다.
- 오류 코드: `CHAT_REPLY_IN_PROGRESS`(409, `CHAT_009`, "다른 창에서 답을 받는 중이에요").
- Redis 키 규칙: `RedisConfig` 주석에 `chat:streaming:{sessionId}` 추가.
- 테스트 의존성 `reactor-test` 추가(버전은 Spring Boot BOM).
- 프론트: 이 창에서 답을 받는 중인 상담 id를 새 스토어 `useChatStreamingStore`에 둔다. `finishStreaming(id)`는 같은 상담일 때만 비워, 늦게 끝난 이전 상담이 새 상담 기록을 지우지 않는다. `ChatRoomSection`이 스트리밍 말풍선이 떠 있는 동안 기록하고, 말풍선이 사라지거나(완료 후 기록 다시 받기까지·오류·취소) 컴포넌트가 사라지면 비운다. 채팅창·마이페이지 오른쪽 칸이 같은 컴포넌트라 둘 다 적용. 2단계에서 같은 창의 메시지 기록 무효화를 건너뛸 때 쓴다.
- `CHAT_009` 문구 "다른 창에서 답을 받는 중이에요. 답이 끝나면 다시 보내 주세요", 보내려던 내용은 입력창에 되돌린다. 되돌리기는 기존에 선언만 있고 처리되지 않던 `restoreInput` 동작을 처리하는 방식이라, 같은 동작인 A003(로그인 만료)·알 수 없는 실패도 이제 입력을 되돌린다.
- 문서: `docs/api/api-spec.md`에 메시지 보내기 API 오류 목록과 보내기 순서 추가.
- DB 변경 없음.

## 검증

| 명령·시나리오 | 실행 환경·조건 | 통과/실패/미실행 | 근거 |
|---|---|---|---|
| `./gradlew clean compileJava compileTestJava test` | 로컬, Testcontainers(MySQL·Redis) | 통과 | 1278건, 실패 0 |
| `ChatStreamLockServiceTest` | 실제 Redis | 통과 | 4건: 토큰·TTL, 중복 409, 내 토큰 해제 후 재획득, 다른 토큰은 지우지 않음 |
| `ChatStreamConcurrencyTest` | 실제 Redis | 통과 | 2건: 동시 두 번 → 하나 409·하루 횟수 1, 완료·오류·취소 뒤 다시 보내기 |
| `ChatStreamServiceTest` | 단위 | 통과 | 12건, 잠금 순서·해제 |
| `pnpm lint`·`typecheck`·`build`·`test` | 로컬 | 통과 | 테스트 132건 |
| `pnpm format:check` | 로컬 | 바꾼 파일만 통과 | 전체는 기존 CRLF 문제로 실패하던 상태 |
| 두 창에서 실제로 동시에 보내기 | 브라우저 | 미실행 | 없음 |

## 연결과 남은 작업

- 실제 커밋·PR: 아직 없음
- 미검증: 두 창 실제 동시 보내기, 브라우저 375/768/1440 확인
- 남은 점: `doFinally`는 마지막 신호 전달 직후 실행되어, `done`을 받자마자 다시 보내면 409가 날 틈이 이론상 있다.
- 다음 단계: 2단계(상담 바뀜 신호를 알림 SSE Redis Pub/Sub에 얹기)
