# 상담 스트림 흐름 정리 (클린 코드 PR3)

상태: 완료 · 작성일: 2026-11-12 · 담당: 이현정

## 목표와 완료 기준
- 사용자 문제: 클린 코드 조사(2026-11-12)의 S1·M1. `ChatStreamService`는 service인데 facade 4개를 불러 be/CLAUDE.md의 레이어 방향과 반대였고, `streamMessage`는 126줄 한 메서드에 동기 검사와 Reactor 조립이 섞여 있었다.
- 확인 가능한 완료 조건
  - 클래스를 `chat/facade/ChatStreamFacade`로 옮기고 이름만 바꾼다(동작 그대로).
  - `streamMessage`를 단계별로 나눠도 잠금 해제·하루 횟수 되돌리기·단어 차단·오류 응답 시점과 연산자 순서가 그대로다.
  - 시점 테스트를 먼저 보강하고, 나눈 뒤에도 잠금·되돌리기·동시성 테스트가 그대로 통과한다.

## 근거와 범위
- 브랜치 `refactor/clean-code-chat-stream`. PR2(`refactor/clean-code-chat-small`) 위에서 땄다.
- 변경할 영역: `domain/chat`만. API·DB·설정 변화 없음. 다른 service가 이 클래스를 부르는 곳(역방향)은 없었다.
- 문서(chat.md, chat-api.md)에는 클래스 이름이 없어 고칠 곳이 없었다.

## 계획
| 단계 | 변경 | 검증 |
|---|---|---|
| 0 | 시점 테스트 3개 추가 | 지금 코드에서 통과, 일부러 틀린 코드에서 실패 확인 |
| 1 | ChatStreamService → ChatStreamFacade (git mv, 테스트 3개 같이) | 상담 테스트 + 재생 테스트 |
| 2 | streamMessage 나누기 | 상담 테스트 + 재생 테스트, 시점 테스트 이름 확인, `./gradlew clean test` |

## AI 활용과 개발자 판단
- AI에 맡긴 작업: 시점 테스트 현황 조사, 빈틈 테스트 작성, 옮기기·이름 바꾸기, 메서드 나누기.
- 개발자 결정: 시점 테스트 3개를 먼저 추가, `ChatStreamConcurrencyTest`·`ChatStreamSafetyLimitTest`는 facade 테스트 패키지로 옮기고 이름은 그대로.

## 구현 결과
- 커밋 0 `test: 상담 스트림 잠금·횟수 시점 테스트 보강`
  - done·error 이벤트를 받는 순간에는 아직 잠금을 풀지 않는다(assertNext 안에서 release never), 첫 조각 전 취소에도 잠금을 풀고 횟수는 돌려주지 않는다.
  - 기존 테스트는 끝난 뒤 "한 번 풀었다"만 봐서 해제가 앞당겨져도 못 잡았다. `doFinally`를 `doFirst`로 일부러 바꾸면 새 테스트 2개만 실패하는 것을 확인하고 되돌렸다.
- 커밋 1 `refactor: ChatStreamService를 facade로 옮기고 이름 바꾸기`
  - git mv, `@Service` → `@Component`(다른 facade 30개와 같게). 의존성 11개 그대로.
  - 부르는 곳 `ChatMessageController`(필드 `chatStreamFacade`). 테스트: `ChatStreamServiceTest` → `ChatStreamFacadeTest`, `ChatStreamConcurrencyTest`·`ChatStreamSafetyLimitTest`는 facade 테스트 패키지로(이름 그대로), `ChatMessageControllerTest` 목 이름, `ChatDailyLimitServiceTest` 주석.
- 커밋 2 `refactor: streamMessage를 단계별로 나누기`

| 단계 | 나누기 전 | 나눈 뒤 |
|---|---|---|
| 동기 1~3 (세션 검사, 잠금, 단어 차단) | streamMessage | streamMessage (그대로) |
| 동기 4 (하루 횟수, 실패하면 잠금 풀고 던짐) | streamMessage 안 try/catch | `countOrRelease` |
| 상태 4개 (받은 글, 첫 조각, 계획, 카드 설명) | 지역 변수 | `ReplyState` (요청마다 새로) |
| 5 의도·후보 → 카드·답 글 | streamMessage 안 `fromCallable.subscribeOn.flatMapMany` | `messageStream` + 세 갈래 `planEvents` |
| 알레르기·건강 안내 | streamMessage 안 `Flux.defer` | `noticeStream` |
| 6 저장·done | streamMessage 안 `Mono.defer` | `doneStream` |
| 7·8 `concat → onErrorResume(첫 조각 전이면 refund) → doFinally(release)` | streamMessage 끝 | `replyStream` (같은 연산자, 같은 순서) |

  - 메서드 길이: streamMessage 21줄, replyStream 19줄, planEvents 40줄, noticeStream 24줄, 나머지 10줄 안팎(전에는 streamMessage 126줄).

## 검증
| 명령·시나리오 | 실행 환경·조건 | 통과/실패/미실행 | 근거 |
|---|---|---|---|
| 시점 테스트 3개, 나누기 전 | 로컬 | 통과 | ChatStreamServiceTest 68개 |
| 시점 테스트, 일부러 잠금을 먼저 풀게 바꿈 | 로컬 | 새 테스트 2개 실패 (의도대로) | 되돌림 |
| `./gradlew test --tests 'org.example.takecareofmyfridgebe.domain.chat.*'` (커밋 1·2) | 로컬, Docker | 통과 | 812개, 수동 평가 1개 건너뜀. ChatIntentReplayTest 기준값 그대로 |
| 잠금·되돌리기·동시성·안전 한도 테스트 이름 확인 (커밋 2 뒤) | 위 실행 | 40개 모두 PASS | ChatStreamFacadeTest, ChatStreamConcurrencyTest(실제 Redis), ChatStreamSafetyLimitTest |
| `./gradlew clean test` | 로컬, Docker | 통과 | 2,367개, 실패 0. 건너뜀 2개는 수동 테스트(`ChatIntentEvalManualTest`, `IngredientNameSearchServiceManualTest`) |

## 연결과 남은 작업
- 실제 커밋·PR: 브랜치 `refactor/clean-code-chat-stream`. push·PR은 하지 않았다.
- 남은 작업: Claude Docs 문서 "냉장고를 부탁해 — 재료 매칭·AI 상담·레시피 추천 코드 흐름"이 `ChatStreamService`·`chat/service`로 적고 있다(개요 표, 2-1·2-2절, 4-2 파일 순서, 5절 표·그림). PR2의 TemperatureWordChecker 경로와 함께 머지 뒤에 고친다.
- 다음 PR: 4. chat 추천(S3, S2·M2, M3, M6), 5. review, 6. fridge·cooking.
