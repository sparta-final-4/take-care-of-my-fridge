# WORK-CHAT-JEV-ON: 상담 의도 Jev on 모드 연결

상태: 검증 대기 (단위 테스트 통과, 실제 키 평가 c2 전) · 작성일: 2026-11-17 · 담당: 상담(chat)

## 목표와 완료 기준
- 사용자 문제: 상담 의도를 OpenAI + 단어 보정(IntentCorrector)으로 정하고 있어, 보정 단어 목록이 계속 늘어난다. Jev(분류 모델)가 의도·요청 종류·분류·맛을 정하게 바꿔 단어 보정을 지울 수 있는 길을 연다.
- 완료 조건: `app.chat.jev.mode`(off / shadow / on)로 고를 수 있고, on이면 Jev 판단으로 의도를 정하고(못 쓰면 보정 없는 OpenAI), off·shadow는 지금과 같다. 기존 테스트(ChatIntentReplayTest 포함)가 고치지 않고 통과한다.

## 근거와 범위
- 설계: [docs/testing/jev-on-design.md](../testing/jev-on-design.md), 넓은 요청 프롬프트: [broad-request-llm-check.md](../testing/broad-request-llm-check.md) v2, Jev 평가: [eval-results/jev-v4](../testing/eval-results/jev-v4/)
- 변경 영역: 백엔드 chat 도메인(ai·config·dto·facade·intent·service), infra/typesafe, application.yml, prompts
- 사용자가 정한 것: B안(Jev 실패·confidence < 0.8이면 보정 없는 OpenAI, 서비스 규칙만), 기준 0.8 상수, 설계 문서의 경계·캐시 키·OpenAI 칸 결정, 밀키트·냉장고 범위·세일은 지금 단어 체커, 이번엔 아무것도 지우지 않음
- 이 브랜치는 배포된 적이 없어 `CHAT_JEV_ENABLED` → `CHAT_JEV_MODE`를 호환 코드 없이 바꿨다

## 계획
| 단계 | 변경 | 검증 |
|---|---|---|
| 1 | 설정 off·shadow·on | ChatJevPropertiesTest, Jev 그림자 테스트 |
| 2 | 넓은 요청 요리 이름 (비슷한 요리 코드를 종류로 넓힘) | SimilarDishFinder·Store·Generator 테스트 |
| 3 | on 모드: JevIntentResolver, facade 연결, 보정 단계 선택, 비교 로그 결정 칸 | ChatIntentFacadeJevOnTest, JevIntentShadowTest, JevComparisonLogTest |

## AI 활용과 개발자 판단
- AI에 맡긴 작업: 설계 문서대로 구현, 테스트 작성, 줄 수 집계
- 채택: 비슷한 요리 클래스를 복사하지 않고 `DishListKind`(SIMILAR / BROAD)로 넓힘 — 다른 것은 프롬프트와 캐시 키 접두어뿐이고 캐시·정리·실패 처리가 같아서
- 채택: 보정 건너뛰기는 스위치를 바꾸지 않고 `IntentCorrector.correct(input, result, Steps)`로 고름 (off·shadow는 `Steps.ALL`)
- 설계와 달라진 점: `ChatIntentResolution.Source.JEV`는 더하지 않았다(쓰는 곳이 없고 결정은 비교 로그 `decision` 칸에 남는다). on에서 OpenAI 의도 판단 자체가 실패하면 지금 실패 처리를 쓰고 `decision=OPENAI_FAILED`로 남긴다(지시서 목록에 없던 값)
- 개발자 확인 필요: on 모드 실제 키 평가(c2), 넓은 요청 캐시 키가 메시지 전체가 되는 경우(preferenceText 없음)

## 구현 결과
- 설정: `ChatJevProperties(Mode mode)`, `app.chat.jev.mode: ${CHAT_JEV_MODE:off}`. shadow = 지금 그림자 모드
- 넓은 요청 요리 이름: `DishListKind`, `SimilarDishStore.find/save(memberId, kind, text)`(키 `chat:broad-dishes:v1:{공백 제거·소문자}`, TTL 같은 설정), `SimilarDishGenerator.generate(memberId, kind, text)`, `OpenAiSimilarDishGenerator`가 종류별 프롬프트, `SimilarDishFinder.findBroad`, `prompts/chat-broad-dishes.txt`
- on 모드
  - `JevIntentShadow`: `on()`, `await(run)`(Jev 시작부터 TypeSafe 제한 시간까지, 1차·필요한 2차까지 성공한 판단만), `compareWhenDone(..., JevDecision)`
  - `JevIntentResolver`(새, intent): Jev 의도·맛 + 요청 종류(경계 규칙) + OpenAI가 꺼낸 값으로 IntentResult
  - `ChatIntentFacade`: on이면 OpenAI 뒤 Jev를 기다려 confidence ≥ 0.8이면 Resolver, 아니면 OpenAI 결과. 둘 다 `Steps.SERVICE_RULES`
  - `IntentCorrector.Steps`: SERVICE_RULES는 레시피 자세히 대상, 요리 이름이 메시지에 있는지, 다른 맛 지키기, 말한 맛만. 상관없는 질문 되살리기·냉장고/사기 말·밀키트 요청·이름만/추천 시도·분류 옮기기·요리 이름 채우기(첫 키워드, ○○ 같은)·맛 단어·분류 단어는 건너뜀
  - `JevComparisonLog`: 끝에 `decision`, `jevUsedKind`, `jevUsedValues` 칸 (값 안 공백 제거)
- 늘어난 줄 수 (2026-11-17, 커밋 e8570982 이후)
  - main: +534 / −95 (순 +439). 새 파일 JevIntentResolver 133, JevDecision 42, DishListKind 30, 프롬프트 8
  - test: +590 / −33. 새 파일 ChatIntentFacadeJevOnTest 389

## 검증
| 명령·시나리오 | 실행 환경·조건 | 통과/실패/미실행 | 근거 |
|---|---|---|---|
| `./gradlew clean compileJava compileTestJava` | 로컬 | 통과 | BUILD SUCCESSFUL |
| `./gradlew test --tests '*.domain.chat.*'` | 로컬, clean 뒤 | 통과 | 937개, 건너뜀 3, 실패 0 (ChatIntentReplayTest 포함) |
| `./gradlew test` | 로컬 (컨테이너 테스트 포함) | 보고에 적음 | |
| on 모드 실제 키 평가 (c2) | - | 미실행 | 다음 작업 |

## 연결과 남은 작업
- 커밋: 56f7d737(설정), 16009d8d(넓은 요청 요리 이름), on 모드 연결 커밋 (push 안 함)
- 다음: c2 평가 — `ChatIntentEvalManualTest`에 Jev 모드를 주고 on 모드 전체 흐름을 all-on(120)과 비교 (설계 문서 8절)
- 결정 대기: on을 기본으로 바꿀 시점, 되돌림 비율을 본 뒤 IntentCorrector 고치기 단계·단어 체커 삭제(설계 문서 9절)
