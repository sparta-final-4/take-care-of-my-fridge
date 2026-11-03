# WORK-139: AI 상담 1단계 — 가짜 답변기를 OpenAI 스트리밍으로 교체

상태: 검증 대기 (실제 키 호출 미실행) · 작성일: 2026-11-03 · 담당: zcookiez

## 목표와 완료 기준

- 요리 상담 일반 답변을 가짜 답변기 대신 OpenAI(gpt-4o-mini) 스트리밍으로 받는다.
- 답을 하나도 못 받고 실패하면 하루 상담 횟수를 돌려준다.
- 키가 없는 로컬에서도 서버가 뜨고 가짜 답변기로 동작한다.

## 근거와 범위

- 선행 작업: WORK-134 같은 상담 동시 답변 잠금. 사전 조사(Spring AI 2.0.1·OpenAI Java SDK 4.49.0 소스 확인) 결과를 따른다.
- 범위: 요리 상담 일반 답변만. 추천 카드·냉장고 재료·대화 메모리·조건 뽑기는 다음 단계.
- 변경 영역: 백엔드 chat 도메인·AI 설정·공통 오류 코드·설정 파일, 프론트 공통 오류 문구.
- 정책:
  - 첫 조각 전 실패와 빈 답은 하루 횟수를 돌려준다.
  - 첫 조각 뒤 실패(CHAT_012)와 사용자 취소는 돌려주지 않는다.

## AI 활용과 개발자 판단

- 맡긴 작업: 조사 결과대로 생성기 선택·상담용 ChatClient·오류 분류·하루 횟수 돌려주기·프론트 문구·테스트 구현.
- 채택:
  - 상담용 ChatClient에는 CostAdvisor를 달지 않는다. 스트리밍에서 `BaseAdvisor`는 finishReason 조각에만 `after()`를 부르는데, OpenAI는 사용량을 그다음 빈 choices 조각에 보낸다. 그래서 비용은 생성기에서 사용량 조각을 직접 보고 기록한다.
  - OpenAI 예외 분류는 생성기 안에서 한다(`onErrorMap`). 첫 조각을 보냈는지 생성기가 알기 때문이다. ChatStreamService는 첫 조각 여부로 돌려주기만 판단한다.
  - 하루 횟수 키에 날짜가 들어 있어서 `checkAndIncrement`가 쓴 키를 돌려받아 그 키로 줄인다. 자정을 넘겨도 맞는 날짜 키를 줄인다.
  - 줄이기는 Lua("키가 있고 0보다 클 때만")로 한다. 만료된 키에 DECR하면 TTL 없는 -1 키가 생기기 때문이다.
- 바꾼 판단:
  - 생성기 인터페이스에 memberId를 더했다(사용량 로그에 회원 ID가 필요).
  - 그 밖의 OpenAI 오류(400·403·404 등)는 지시대로 바꾸지 않아 공통 서버 오류(COMMON_002)로 간다.

## 구현 결과

백엔드

- `ChatReplyGenerator`: `generate(memberId, sessionId, userMessage)`로 바꿨다.
- `StubChatReplyGenerator`: `@ConditionalOnProperty(app.chat.reply-generator=stub, 값이 없을 때도)`
- `OpenAiChatReplyGenerator` (새로 만듦)
  - `@ConditionalOnProperty(app.chat.reply-generator=openai)`
  - `.stream().chatResponse()`에서 글자만 꺼내고 빈 문자열은 거른다.
  - 사용량 로그는 스트림이 끝날 때(doOnComplete) 한 번만 남긴다(INFO: 회원·상담·모델·입력/출력 토큰·비용). 값은 마지막으로 받은 0보다 큰 사용량을 쓴다. 끝까지 못 받으면 WARN "사용량을 받지 못함"을 남긴다.
    - 처음에는 조각마다 기록했다. 그런데 실제 호출에서 사용량이 없는 조각도 0으로 채운 사용량을 담아 와 "입력 0, 출력 0" 로그가 수십 줄 찍혔다. 실제 사용량(예: 입력 163, 출력 37)은 마지막 조각에만 있었다.
  - finishReason이 length면 WARN 로그를 남긴다.
- `ChatReplyErrorTranslator` (새로 만듦): cause 사슬을 따라가 OpenAI 예외를 찾는다.
  - 401·잔액 부족 → CHAT_010 (ERROR 로그)
  - 5xx·타임아웃·연결 실패 → CHAT_010
  - 요청 한도 429 → CHAT_011
  - 첫 조각 뒤 → CHAT_012
- `ChatDailyLimitService`: `checkAndIncrement`가 키를 돌려준다. `refund(key)`를 추가했다(Lua).
- `ChatStreamService`
  - 첫 조각 여부를 AtomicBoolean으로 표시한다.
  - 첫 조각 전 실패는 하루 횟수를 돌려준다.
  - 빈 답은 CHAT_010 오류를 보내고, 하루 횟수를 돌려주고, 저장하지 않는다.
  - 잠금 해제는 기존 그대로 doFinally에서 한다.
- `ChatClientConfig`: `chatReplyChatClient` 빈을 추가했다.
  - gpt-4o-mini, maxTokens 800, timeout 30s
  - 시스템 프롬프트는 `prompts/chat-system.txt`에서 읽는다.
  - 후기 요약 빈은 그대로 뒀다.
- `ErrorCode`: CHAT_010 `CHAT_AI_UNAVAILABLE`(503), CHAT_011 `CHAT_AI_BUSY`(429), CHAT_012 `CHAT_REPLY_INTERRUPTED`(502)를 추가했다.
- `resources/prompts/chat-system.txt` (새로 만듦): 시스템 프롬프트 초안이다.
  - 중괄호 `{}`는 템플릿 변수로 해석되므로 쓰지 않는다.
- `application.yml`: `app.chat.reply-generator: ${CHAT_REPLY_GENERATOR:stub}`, 잠금 TTL 주석을 갱신했다.
- `application-prod.yml`: `app.chat.reply-generator: ${CHAT_REPLY_GENERATOR:openai}`

프론트

- `common/api/errors.ts`: CHAT_010·011은 문구와 입력 되돌리기, CHAT_012는 문구만 보여준다.

API·DB 변경 없음. SSE `error` 이벤트에 새 코드 3개가 추가된다.

## 검증

| 명령·시나리오 | 실행 환경·조건 | 결과 | 근거 |
|---|---|---|---|
| `./gradlew clean compileJava compileTestJava` | 로컬 | 통과 | 오류·경고 없음 |
| `./gradlew clean test` | 로컬, Docker | 통과 | 1385개, 실패 0, skip 1(기존). 사용량 로그 수정 후 다시 실행 |
| 전체 컨텍스트 테스트 6개 | 위와 같음 | 통과 | ApplicationTests 등 6개 클래스 실행됨 |
| `pnpm lint`, `typecheck`, `test`, `build` | 로컬 | 통과 | test 164개 |
| `pnpm format:check` | 로컬 | 실패(기존) | 변경 전에도 36개 파일 실패. 바꾼 2개 파일은 통과 |
| 키 없이 서버 기동 (stub) | `OPENAI_API_KEY` 없음, 로컬 Docker | 통과 | 7.8초에 기동. 메시지 1개에 조각 11개와 `done`을 받아 저장됨 |
| 잘못된 키 (openai) | `CHAT_REPLY_GENERATOR=openai`, 가짜 키 | 통과 | `error {"code":"CHAT_010"}`, ERROR 로그 `status: 401, code: invalid_api_key`. 하루 횟수가 1 → 1(늘린 뒤 돌려줌) |
| 실제 키 스트리밍 | — | 미실행 | 로컬에 OpenAI 키 없음 |

- 키 없는 기동: `${OPENAI_API_KEY}`에 기본값이 없어도 서버가 뜬다. 그래서 키 기본값은 추가하지 않았다.
- 테스트 정책 변경 두 곳:
  - `ChatStreamConcurrencyTest`의 오류 단계는 첫 조각 전 실패라 하루 횟수가 돌아간다. 기대값을 4에서 3으로 바꿨다.
  - `ChatDailyLimitServiceTest` 주석의 "답 생성이 실패해도 차감 유지"를 새 정책에 맞게 고쳤다.

## 연결과 남은 작업

- 실제 커밋·PR: 아직 없음.
- 미검증 항목:
  - 실제 키 스트리밍 (조각 수신, 마지막 사용량 조각 순서, 비용 로그)
  - timeout 30s가 요청에 실제로 적용되는지 (소스로만 확인)
  - OpenAI 예외가 실제로 몇 겹으로 감싸져 오는지 (401은 찾아서 분류됨)
- 배포 담당 공유:
  - `CHAT_REPLY_GENERATOR`: 선택. prod 기본값 openai, 장애 때 stub
  - `OPENAI_API_KEY`: 기존 키를 후기 요약·검색 임베딩과 같이 씀
- 다음 단계: 추천 카드·냉장고 재료·대화 메모리·조건 뽑기.
