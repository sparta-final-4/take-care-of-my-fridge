# 상담 안전 장치 ④ 하루 횟수 규칙 정리: AI를 썼으면 센다

상태: 검증 대기 · 작성일: 2026-11-07 · 담당: 상담

## 목표와 완료 기준
- 사용자 결정: 남용 제한(연속 차단 Redis 키·하루 안 센 횟수 한도)과 Moderation은 하지 않는다. 대신 규칙을 "AI를 썼으면 하루 횟수를 센다" 하나로 정리한다.
- 완료 조건:
  - 단어 차단(욕설·자해·탈취, PR①)은 AI를 쓰지 않으므로 지금처럼 세지 않는다.
  - 의도 판단으로 걸린 것(OFF_TOPIC·BLOCKED·SELF_HARM, PR②)은 의도 판단 AI를 썼으므로 센다(돌려주지 않는다).
  - 안내 문장과 filtered 저장은 그대로 둔다(SELF_HARM은 109 안내).

## 근거와 범위
- 근거: PR① [단어 차단](work-chat-safety-words.md), PR② [서버 안내 의도](work-chat-safety-off-topic.md)
- 변경 영역: `domain/chat/service/ChatStreamService`, dto 주석(`ChatIntent`, `ChatIntentResolution`), 테스트, API 명세
- 이 브랜치(`feat/chat-safety-abuse-limit`)는 `refactor/chat-config-split`(설정 파일 나누기, `b811e557`) 위에 있다. 그 커밋이 dev에 먼저 들어가야 이 PR에 이번 변경만 보인다.

## 구현 결과
- `ChatStreamService`
  - 의도 판단 서버 안내를 위해 넣었던 "한 번만 돌려주기"(`refunded`·`refundOnce`)와, 저장 뒤 돌려주는 부분을 지웠다.
  - 돌려주기는 원래 규칙(답 조각을 하나도 받기 전에 실패하면 돌려줌)만 남는다.
  - 의도 판단 서버 안내는 안내 글이 첫 조각이라 저장이 실패해도 돌려주지 않는다. AI를 썼으므로 센다.
- `ReplyPlan.uncounted()` → `serverNotice()`: 이제 하루 횟수와 상관없고, 건강·알레르기 안내를 붙이지 않는 답인지만 뜻한다.
- 단어 차단(`blockedReply`)은 그대로다. 하루 횟수를 늘리기 전에 걸러 세지 않는다.
- API 명세: 서버 안내 의도의 하루 횟수 설명을 "센다"로 고쳤다. PR② 작업 기록에는 바뀐 사실을 적었다.
- API 요청·응답 모양, DB, 에러 코드, 프론트 변경 없음

## 검증
| 명령·시나리오 | 실행 환경·조건 | 결과 | 근거 |
|---|---|---|---|
| `./gradlew clean compileJava compileTestJava test` | 로컬 | 통과 | 2,159개, 실패 0, 건너뜀 1(기존 수동 테스트) |
| 단어 차단은 그대로 | `ChatStreamSafetyLimitTest`, Redis 컨테이너 | 통과 | 욕설·자해·탈취 4번 보내도 키 없음 |
| 의도 판단 안내는 셈 | `ChatStreamSafetyLimitTest`, Redis 컨테이너 | 통과 | OFF_TOPIC 3번 → "3", BLOCKED·SELF_HARM 각 1번 → "2" |
| 돌려주지 않음 | `ChatStreamServiceTest` | 통과 | OFF_TOPIC·BLOCKED·SELF_HARM `refund` 호출 없음, OFF_TOPIC 저장 실패에도 없음 |

## 연결과 남은 작업
- 커밋: `feat/chat-safety-abuse-limit` 로컬 커밋 (push 안 함)
- 하지 않기로 한 것: 연속 걸림 차단(`CHAT_013`), 하루 안 센 횟수 한도, Moderation API
