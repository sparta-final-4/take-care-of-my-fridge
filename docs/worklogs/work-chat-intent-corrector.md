# AI 상담 의도 보정 정리: IntentCorrector와 AI 요리 이름

상태: 완료 (평가 115/115) · 작성일: 2026-11-11 · 담당: zcookiez

브랜치 `refactor/chat-intent-corrector`(`fix/chat-recipe-fill` 위)에서 한 작업이다. 커밋은 로컬에만 있고 push·PR은 하지 않았다.
앞선 기록: [의도 판단 서버 보정을 설정으로 끌 수 있게](work-chat-server-corrections.md), [의도 판단 프롬프트 줄이기](work-chat-intent-prompt.md).

## 목표와 완료 기준
- 사용자 문제
  - "디저트"·"국물"처럼 분류 이름·묶음 말만 말해도 서버가 첫 키워드를 요리 이름으로 삼아 "디저트 레시피는 없어서…" 같은 엉뚱한 안내가 붙었다.
  - 요리 이름이 정해지는 곳이 세 군데(의도 단계 첫 키워드, 추천 단계 특징 비우기, 추천 단계 키워드로 채우기)라 어디서 바뀌는지 따라가기 어려웠다.
  - 의도 판단 뒤 서버 보정 10개가 `ChatIntentFacade` 한 파일(약 900줄)에 섞여 있었다.
- 확인 가능한 완료 조건
  - 요리 이름은 AI가 뽑고(`dishText`), 서버는 메시지에 그대로 있고 분류 이름·묶음 말이 아닌지만 확인한다. 추천 단계에는 요리 이름을 추측하는 코드가 없다.
  - 분류 이름·묶음 말인 키워드는 한 곳에서 분류로 옮긴다.
  - 서버 보정은 `IntentCorrector` 한 클래스에서 정해진 순서로 돈다.
  - 보정을 옮기기만 한 커밋은 재생 테스트(기록한 AI 결과 → 최종 결과)가 기대값 변경 없이 통과한다.
  - 평가 세트(115문장 × 3번 다수결)가 바꾸기 전 이상으로 맞는다.

## 근거와 범위
- 변경할 영역: 백엔드 `domain/chat`(facade·dto·config), 의도 프롬프트 `prompts/chat-intent.txt`, `config/chat-words.yml`, `application.yml`, 평가·재생 테스트.
- 다른 도메인 영향: 없음. API·DB 변경 없음. 프런트 변경 없음.
- 정한 것 (사용자)
  - 특징 표현 대비책("마지막 낱말이 분류·일반 말이면 비움")은 넣지 않는다. 평가로 AI가 특징을 요리 이름으로 내지 않는 것을 확인했다(T02).
  - 요리 이름 확인과 분류 키워드 옮기기는 `IntentCorrector` 안 한 메서드에서 한다.
  - 다시 추천(REFINE)은 지금처럼 요리 이름을 비운다.
  - 분류 이름 키워드는 빼기만 하지 않고 분류에 더한다. 피하는 분류("디저트 말고")면 옮기지 않는다.
  - 스위치 `dish-check`는 남긴다 (끄면 AI 요리 이름을 확인 없이 씀, 평가 비교용).

## 계획과 커밋
| 단계 | 커밋 | 동작 | 검증 |
|---|---|---|---|
| 1 | `dbc23013` test: 의도 보정 기록 재생 테스트 | 그대로 | `ChatIntentReplayTest` 기준값 기록 |
| 2 | `7afdd3e3` refactor: 의도 보정을 IntentCorrector로 옮기기 | 그대로 | 재생 테스트 기대값 변경 없이 통과 |
| 3 | `f97d46d8` fix: 분류 이름·묶음 말을 키워드에서 빼기, `1e9fecf3` fix: 띄어 쓴 묶음 말도 분류로 | 바뀜 | 재생 31줄 + 3줄 갱신, 평가 115/115 |
| 3-1 | `0470ed03` fix: 분류 이름 키워드는 분류로 옮기기 | 바뀜 | 재생 R04 3줄 갱신 |
| 4 | `1a7917e7` feat: 요리 이름은 AI가 뽑고 서버는 확인만 | 바뀜 | 평가 112/115 → 프롬프트 보완 |
| 4-1 | `6e86d0a1` fix: 요리 이름 예시 보완 ("같은 요리", 키워드는 따로) | 바뀜 (프롬프트) | 평가 115/115 |
| 4-2 | `4759b9eb` test: 재생 입력에 AI 요리 이름 넣기 | — | step4 평가 결과로 재생 입력·기준값 다시 기록 |
| 5 | `b79be922` refactor: 추천 단계 요리 이름 추측 지우기 | 바뀜 (추천 단계만) | 단위 테스트, 전체 테스트. 의도 단계는 안 바뀌어 평가는 건너뜀 |
| 6 | test: 평가 세트 정리, docs: 의도 보정 정리 작업 기록 | — | 이 문서 |

## 평가 결과
`ChatIntentEvalManualTest`, gpt-4o-mini, 115문장 × 3번 다수결. 결과 파일은 `take-care-of-my-fridge-be/build/reports/chat-intent-eval/`.

| 시점 | 파일 | 전체 일치 | 틀린 문장 | 첫 질문 프롬프트 (분류 목록 포함) | 평균 출력 |
|---|---|---|---|---|---|
| 바꾸기 전 (임시 X01~X05 포함) | `eval-20261110-223652-before` | 110 / 115 | R10(띄어 쓴 묶음 말), X01~X04(분류 이름이 요리 이름이 됨) | 1,627토큰 | — |
| 3번 뒤 (라벨은 before) | `eval-20261110-233536-before` | 115 / 115 | — | 1,627토큰 | — |
| 4번 뒤 | `eval-20261111-093256-step4` | 112 / 115 | R04 원한 표현 1/3, R06 요리 이름 김피탕 0/3("같은 요리"라 AI가 비움), T16 키워드 0/3(예시가 dishText만 보여 줘 AI가 키워드까지 비움) | 2,952자 / 1,755토큰 | 79토큰 |
| 4-1 뒤 | `eval-20261111-101011-step4b` | 115 / 115 | — | 1,879토큰 | 79토큰 |

- T02 "칼칼한 국물 요리": AI가 3번 모두 요리 이름을 비웠다(키워드 칼칼한 국물·매운 국물·얼큰한 국물). 특징을 요리 이름으로 내지 않아 특징 표현 대비책은 넣지 않았다.
- 5번(추천 단계)은 의도 단계가 바뀌지 않아 평가를 돌리지 않았다. 바뀐 동작은 `ChatRecommendationFacadeTest`(탕짜면·닭볶음탕 안내)로 본다.
- 프롬프트 크기(파일 글자 수): 2,640 → 2,850(요리 이름 규칙) → 3,110(예시 보완). `ChatIntentPromptTest` 상한 2,800 → 3,300자.

## 구현 결과
- `IntentCorrector`(새, 약 640줄): `ChatIntentFacade`에 있던 보정 10개를 옮겼다. 순서는 상관없는 질문 되살리기 → 자세히 대상 찾기 → 냉장고·사기 말 → 밀키트 요청 → 이름만 말하기 → **요리 이름·분류 키워드 확인**(`checkDishAndKeywords`) → 다른 맛 지키기 → 맛 단어 → 분류 단어 → 말한 맛만. 결과를 추천 조건으로 바꾸고 다시 추천을 합치는 일은 `ChatIntentFacade`에 남겼다(약 480줄 줄어듦).
- `checkDishAndKeywords` 한 곳에서
  - AI 요리 이름은 메시지에 그대로 있고(공백 무시) 분류 이름·묶음 말이 아닐 때만 둔다. 아니면 비운다. 스위치 `app.chat.corrections.dish-check`.
  - 분류 이름·묶음 말인 키워드(`categoriesOf`: 분류 목록 + `category-words.groups`, 뒤에 붙은 "요리·메뉴·종류"는 뗌)는 키워드에서 빼고 분류로 옮긴다. 이번 말에서 피하는 분류는 옮기지 않는다.
- `IntentResult.dishText`: `@JsonIgnore`를 떼어 AI 스키마에 넣었다. `withDishFromKeywords` 삭제. 이름만 말하기(레시피 이름 찾음)와 자세히 대상 못 찾음은 `withNamed(includes, keywords, dish)`로 요리 이름을 직접 넣는다.
- `chat-intent.txt`: `dishText` 규칙과 예시 4줄("○○ 같은 요리"도 요리 이름, 분류·특징은 빈 문자열, keywords는 따로 채움).
- `CategoryWordChecker`: 4글자 이상 묶음 말("간단한한끼")은 띄어 쓴 메시지에서도 찾는다 (R10).
- 추천 단계(`ChatRecommendationFacade`)에서 지운 것: 특징 표현 비우기(`isFeature`), 키워드로 요리 이름 채우기(`spokenDishWithoutRecipe`), `FeaturePhraseChecker`, `ChatFeatureWordProperties`, `chat-words.yml`의 `feature-words`, `RecipeQuery.withKeywordText`. 운영 코드 −150줄 / +3줄, 테스트 −81줄 / +25줄.
- 스위치: `dish-from-keywords` → `dish-check`로 이름과 뜻을 바꿨다. 평가에서 `OPENAI_INTENT_EVAL_OFF=dish-check`로 AI 요리 이름만의 결과를 비교할 수 있다. 나머지 9개는 그대로.
- 브랜치 전체 변화(`85cc2f8e` 기준): 운영 코드 +691 / −664줄(옮긴 코드 포함), 테스트 +483 / −139줄, 테스트 자원 +680줄(재생 입력·기준값).

### 재생 테스트 (`ChatIntentReplayTest`)
- 입력 `src/test/resources/chat-intent-replay/inputs.csv`: 평가 `runs.csv`의 AI 원래 값 칸(`aiIntent` ~ `aiQuestion`, 4번부터 `aiDish` 포함)을 그대로 옮긴 339줄(단어 차단으로 AI를 안 부른 B02·S01은 뺌). 지금 입력은 step4 평가(`eval-20261111-093256-step4-runs.csv`)에서 만들었다.
- 기준값 `expected.csv`: 서버 보정을 거친 최종 값. 동작을 일부러 바꾸는 커밋에서만 `CHAT_INTENT_REPLAY_RECORD=true ./gradlew test --tests '*ChatIntentReplayTest'`로 다시 기록하고, 바뀐 줄을 커밋에 적는다.
- 입력 다시 만드는 법: `runs.csv`에서 `id, context, message, run, aiIntent, aiWanted, aiAvoid, aiCategories, aiKeywords, aiDish, aiPreference, aiMaxMinutes, aiInclude, aiExclude, aiUseSelectedOnly, aiScope, aiTargetIndex, aiTargetName, aiRemoved, aiQuestion` 칸을 그 순서로 뽑고(칸 글자는 따옴표 포함 그대로), `aiIntent`가 빈 줄은 뺀다. 줄바꿈은 LF, BOM 없음.
- 6번에서 더한 평가 문장(R18~R20, N07, N08)은 아직 재생 입력에 없다. 다음 평가를 돌린 뒤 그 `runs.csv`로 입력·기준값을 다시 만든다.

### 평가 세트 정리 (6번)
- 임시 X01~X05(묶음 "조사")를 정식 문장으로: X01 "디저트"는 N04와 같아 N04에 `dishEmpty`만 더함, X02 → R18 "디저트 추천해줘", X05 → R19 "반찬 추천해줘", X03 → N07 "달달한 디저트"(원하는 맛 SWEET 추가), X04 → N08 "국물".
- 요리 이름 기대값 추가: R20 "탕짜면 먹고 싶어"(`dish` 탕짜면). "김피탕 같은 요리"는 R06이 이미 `dish` 김피탕을 본다.
- 문장 수는 115 그대로 (추천 20, 맛 16, 이름만 8, 일반 13, 상관없음 4, 거절 2, 자해 2, 냉장고 14, 냉장고 아님 7, 사기 8, 사기 아님 3, 후속 18).

## AI 활용과 개발자 판단
- AI에 맡긴 작업: 요리 이름이 정해지는 경로 조사, 보정 순서·의존 관계 표, 재생 테스트와 입력 생성, 클래스 옮기기, 프롬프트 문구, 단위 테스트, 이 기록.
- 개발자 판단: 작업 순서와 커밋 단위(옮기기만 / 동작 바뀜 구분), 특징 표현 대비책 넣지 않기, 분류 키워드를 빼지 않고 옮기기, 스위치 유지, 평가 기대값(R20·N07) 확정, 평가 실행(실제 키)과 결과 판독(T02·T16·R06의 AI 원래 값).

## 검증
| 명령·시나리오 | 실행 환경·조건 | 통과/실패/미실행 | 근거 |
|---|---|---|---|
| `./gradlew compileJava compileTestJava test` | 로컬, 커밋마다 | 통과 (실패 0) | build/test-results |
| 재생 테스트 (옮기기 커밋 `7afdd3e3`) | 로컬 | 기대값 변경 없이 통과 | expected.csv diff 없음 |
| 재생 테스트 (동작 바뀜 커밋) | 로컬 | 다시 기록, 바뀐 줄 확인: 3번 31줄, R10 3줄, 3-1 R04 3줄, 4번 37줄(요리 이름 칸, 입력에 AI 요리 이름이 없던 때), 4-2 76줄(입력 교체) | 각 커밋 |
| 평가 바꾸기 전 / 3번 / 4번 / 4-1 | 실제 OpenAI 키, 사용자 실행 | 110 → 115 → 112 → 115 / 115 | 위 "평가 결과" |
| 평가 5번 뒤 | — | 미실행 (의도 단계 변화 없음) | 단위 테스트로 대신 |
| 평가 6번 뒤 (새 문장 R18~R20·N07·N08) | 실제 OpenAI 키 | 미실행 | 다음 평가 때 재생 입력과 같이 |

## 앞으로 지킬 것
- `chat-intent.txt`·`chat-intent-followup.txt`를 바꾸면 평가를 다시 돌린다 (`OPENAI_INTENT_EVAL=true OPENAI_INTENT_EVAL_KEY=... OPENAI_INTENT_EVAL_LABEL=이름 ./gradlew cleanTest test --tests '*ChatIntentEvalManualTest' -i`). AI 출력이 매번 달라 전체 일치율보다 "전에 맞던 문장이 틀린 것"을 본다. `clean`을 쓰면 `build/reports`가 지워지므로 결과 파일은 따로 옮겨 둔다.
- 서버 보정(`IntentCorrector`)을 바꾸면 재생 테스트가 먼저 잡는다. 옮기기만 한 변경은 기준값이 그대로여야 하고, 동작을 바꾼 변경은 다시 기록한 뒤 바뀐 줄을 커밋 설명에 적는다.
- 평가를 돌린 뒤에는 그 `runs.csv`로 재생 입력을 다시 만들어 AI 요리 이름 칸까지 고정한다.
- 요리 이름을 다시 서버가 추측하게 만들지 않는다. AI가 비우면 비운 대로 가고, "○○ 레시피는 없어서" 안내는 AI 요리 이름이 있을 때만 붙는다.

## 연결과 남은 작업
- 실제 커밋: 위 "계획과 커밋". push·PR 안 함.
- 남은 작업: 6번 평가 문장의 재생 입력·기준값 추가(다음 평가 때). PR은 `fix/chat-recipe-fill`이 dev에 들어간 뒤.
- 정리 후보(이번 범위 밖, 조사 기록은 2026-11-11 상담 판단 지도): "요청이 있나" 판단이 네 가지(`RecipeQuery.hasRequest`, `PickRule.hasRequest`, `preferenceUnmet`, `mealkitOnlyReply`), 매운맛 확실/가능 두 등급 해석이 쓰는 곳마다 다름. 둘 다 2·3층(후보·안내) 평가가 있어야 안전하게 손댈 수 있다.
