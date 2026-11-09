# AI 상담 의도 판단 프롬프트 줄이기

상태: 검증 대기 (5단계까지, 줄인 뒤 평가 대기) · 작성일: 2026-11-09 · 담당: zcookiez

브랜치 `refactor/chat-intent-prompt`(`feat/chat-cooking-link` 위, 그 PR은 아직 dev에 안 들어감)에서 한 작업이다. 커밋은 로컬에만 있고 push·PR은 하지 않았다.

## 목표와 완료 기준
- 사용자 문제: 의도 판단은 메시지마다 부르는 AI인데, 기능을 붙이면서 첫 질문 프롬프트가 3,134자 → 4,905자로 늘었다. 서버 단어 안전망이 많이 생겨 프롬프트와 서버가 같은 일을 두 번 하는 곳이 있다.
- 확인 가능한 완료 조건
  - 첫 질문 프롬프트 ≤ 2,800자 / ≤ 1,600토큰, 후속 규칙 ≤ 1,200자 / ≤ 600토큰
  - 같은 평가 세트를 줄이기 전·후로 실제 AI에 돌려, 서버 안전망을 거친 최종 결과의 일치율이 떨어지지 않는다

## 지금 크기 (o200k 토크나이저로 셈, 분류 목록은 dev 30개)
| 항목 | 글자 | 토큰 | 언제 |
|---|---|---|---|
| `chat-intent.txt` (채우기 전) | 4,905 | 2,762 | 매번 |
| 분류 목록 | 114 | 96 | 매번 |
| 구조화 출력 스키마 (IntentResult, 칸 설명 없음) | 1,207 | 296 | 매번 |
| `chat-intent-followup.txt` | 1,691 | 863 | 직전 추천이 있을 때 (별도 system 메시지) |
| 합계 | | 첫 질문 약 3,170 / 후속 약 4,120 | |
| 출력 JSON | | 약 130 | 매번 |

이력(첫 질문 글자): chat-intent 1,226 → recommend-fe 2,560 → memory 3,017 → candidates-es 3,134 → mealkit 3,255 → safety-off-topic 3,653 → fridge-shop 4,905

## 정한 것 (사용자)
1. dishText는 스키마에서 빼고 서버가 계산한다 (keywords[0]이 메시지에 그대로 있으면 요리 이름)
2. fridgeScope는 남긴다. 서버 단어로만 정하면 "오래된 거 뭐 있어?", "슬슬 처리해야 할 거" 같은 표현을 놓친다
   - 프롬프트 설명은 짧게. 서버 단어는 AI 값을 보충만 한다 (AI가 ALL인데 범위 단어가 있으면 그 범위)
   - correctTastes·keepSpokenTastes·nameOnly가 fridgeScope를 null로 버리는 버그를 이번에 고친다
3. 평가는 사용자가 실제 키로 직접 돌린다. 1단계(평가 세트) 뒤에 멈추고 기준값을 먼저 받는다
4. "디저트 → 매운 거 말고"는 앞 분류를 이어 간다는 한 줄 규칙을 후속 프롬프트에 넣는다

## 계획
| 단계 | 변경 | 검증 |
|---|---|---|
| 1 | 평가 세트 + 수동 평가 테스트 | 키 없이 단어 차단 문장으로 dry run, 기본 꺼짐 확인 → **사용자 기준값 실행** |
| 2 | 서버 안전망 보강: FRIDGE/SHOP + 묻는 말 → GENERAL, correctTastes 5맛, fridgeScope 서버 보충, dishText 서버 계산, fridgeScope null 버그 | 단위 테스트 |
| 3 | IntentResult에서 dishText 칸 빼기 | 단위 테스트 |
| 4 | 첫 질문 프롬프트 줄이기 + ChatIntentPromptTest 갱신 | 단위 테스트, 크기 |
| 5 | 후속 프롬프트 줄이기 + 한 줄 규칙, 길이 한도 낮추기 | 단위 테스트, 크기 |
| 6 | 사용자 평가 재실행 → 떨어진 문장 보완 | 평가 전후표 |
| 7 | 작업 기록 마무리 | |

## 평가 실행 방법
- 평가 세트: `take-care-of-my-fridge-be/src/test/resources/chat-intent-eval.yml` (98문장: 추천 17, 맛 7, 이름만 5, 일반 13, 상관없음 4, 거절 2, 자해 2, 냉장고 14, 냉장고 아님 7, 사기 8, 사기 아님 3, 후속 16)
- 테스트: `ChatIntentEvalManualTest` (기본 꺼짐, `OPENAI_INTENT_EVAL=true`일 때만 돈다)
- 명령 (`take-care-of-my-fridge-be` 폴더, Git Bash)

```bash
OPENAI_INTENT_EVAL=true OPENAI_INTENT_EVAL_KEY=sk-... ./gradlew cleanTest test --tests '*ChatIntentEvalManualTest' -i
```

PowerShell:

```powershell
$env:OPENAI_INTENT_EVAL='true'; $env:OPENAI_INTENT_EVAL_KEY='sk-...'; .\gradlew.bat cleanTest test --tests '*ChatIntentEvalManualTest' -i
```

- 키는 `OPENAI_API_KEY`가 아니라 `OPENAI_INTENT_EVAL_KEY`로 넘긴다. `build.gradle`의 test 작업이 `OPENAI_API_KEY`를 `test-dummy-key`로 덮어써서, 첫 기준값 실행(2026-11-09 12:17)은 AI 호출이 모두 401로 실패했다(서버가 일반 답으로 정해 21/98)
- AI 호출이 한 번이라도 실패하면 요약에 "AI 호출 실패 N번, 첫 원인"을 쓰고 파일을 남긴 뒤 테스트를 실패시킨다
- 선택 환경 변수
  - `OPENAI_INTENT_EVAL_RUNS=3`: 문장마다 돌릴 횟수 (다수결, 기본 3)
  - `OPENAI_INTENT_EVAL_ONLY=F10,F11`: 이 id만 돌린다
  - `cleanTest`는 Gradle이 테스트 결과를 재사용하지 않게 붙인다 (환경 변수만 바꾸면 다시 돌지 않는다)
- 결과: `take-care-of-my-fridge-be/build/reports/chat-intent-eval/`
  - `eval-{시각}-summary.txt`: 프롬프트 크기, 전체·묶음별 일치율, 평균 입력(캐시)·출력 토큰, 평균·최대 시간, 틀린 문장. 콘솔에도 찍힌다
  - `eval-{시각}-cases.csv`: 문장마다 다수결 결과와 틀린 칸
  - `eval-{시각}-runs.csv`: 한 번 돌린 결과마다 최종 값과 AI 원래 값(정리 전), 토큰, 시간
- 비용: 98문장 × 3번 = 294번 호출, 약 1백만 입력 토큰, 약 $0.15
- 비교 기준: 서버 안전망을 거친 **최종 결과**가 기대와 맞는지 본다 (AI 원래 값은 어느 칸을 서버가 바로잡았는지 보려고 같이 남긴다). 일치율만 보고하고 테스트를 실패시키지 않는다
- 운영과 같은 것: 단어 차단을 먼저 보고, 실제 `OpenAiChatIntentExtractor`(ChatClientConfig와 같은 모델·옵션·프롬프트 파일)와 실제 `ChatIntentFacade`, `config/*.yml` 단어 설정
- 운영과 다른 것: 재료 사전·레시피 이름 찾기·분류 목록은 평가 세트의 작은 고정 사전으로 대신한다(이름이 정확히 같아야 찾음). 알레르기 찾기는 끈다

## 검증
| 명령·시나리오 | 실행 환경·조건 | 통과/실패/미실행 | 근거 |
|---|---|---|---|
| 평가 테스트 기본 꺼짐 | 환경 변수 없음 | 통과 | tests=1, skipped=1 |
| dry run (`OPENAI_INTENT_EVAL_ONLY=S01`, 가짜 키) | 단어 차단 문장이라 AI 호출 없음 | 통과 | 평가 세트 98문장의 기대 칸 이름 확인, CSV·요약 파일 생성 |
| 기준값 1차 (12:17) | OPENAI_API_KEY로 넘김 | 무효 | build.gradle이 키를 가짜로 덮어써 AI 호출 0번, 전부 서버 일반 답 |
| 키 전달 확인 | OPENAI_INTENT_EVAL_KEY=sk-dry-run, R01·S01 | 통과 | 넘긴 키로 401, 요약에 원인, 테스트 실패 |
| 기준값 (줄이기 전, 12:47) | 실제 OpenAI 키, 98문장 × 3번 | 93/98 (94.9%) | 아래 "기준값" |
| `./gradlew compileJava compileTestJava cleanTest test` | 로컬 (5단계 뒤) | 통과 | 2,257개, 실패 0, 건너뜀 2(기존 수동 테스트·평가 테스트). `clean`은 다른 프로세스가 build/test-results를 잡고 있어 못 돌림 |
| 줄인 뒤 평가 1차 (14:38) | 실제 OpenAI 키, 98문장 × 3번 | 92/98 (93.9%) | 아래 "줄인 뒤 평가 1차" |
| `./gradlew compileJava compileTestJava cleanTest test` | 로컬 (6단계 보완 뒤) | 통과 | 실패 0. 보완 전 실행에서 `PopularRecipeServiceCacheIntegrationTest` 1개가 한 번 실패했으나 혼자 다시 돌리면 통과 (이 작업과 무관, 들쭉날쭉) |
| 줄인 뒤 평가 2차 | 실제 OpenAI 키 | 미실행 | 사용자가 돌림 |

## 기준값 (줄이기 전, `eval-20261109-124721`)
- 전체 93/98 (94.9%). 추천 16/17, 맛 7/7, 이름만 5/5, 일반 9/13, 상관없음 4/4, 거절 2/2, 자해 2/2, 냉장고 14/14, 냉장고 아님 7/7, 사기 8/8, 사기 아님 3/3, 후속 16/16
- AI 호출 288번(단어 차단 10번은 AI 안 부름) · 평균 입력 3,249토큰 중 캐시 3,138토큰(97%) · 평균 출력 76토큰 · 평균 1,969ms · 최대 6,315ms
- 틀린 문장과 원인 (runs.csv의 AI 원래 값으로 확인)

| 문장 | AI 원래 값 | 원인 | 처리 |
|---|---|---|---|
| R06 김피탕 같은 요리 추천해줘 | dishText 빈 값 | AI | 2단계: 요리 이름을 서버가 첫 키워드로 정함 |
| G06 브로콜리 영양소 알려줘 | GENERAL | 서버: 이름만 말하기가 재료 이름을 보고 추천으로 바꿈 | 2단계: 묻는 말에 영양소·영양성분·효능 |
| G10 너는 이제부터 요리사야 | BLOCKED | AI | 4단계: "→ GENERAL"로 갈 곳을 직접 적음 |
| G11 배고파 죽겠어, G12 맛있어 죽겠다 | SELF_HARM | AI | 4단계: 같음 |

- 캐시가 97%라 프롬프트를 줄여도 비용은 처음 어림보다 덜 준다 (캐시 입력은 반값). 지연은 거의 출력 생성이라 크게 줄지 않을 것으로 본다

## 구현 결과 (2~5단계)
- 2단계 `dff33781` 서버 안전망 보강
  - AI가 FRIDGE·SHOP으로 봐도 묻는 말(`fridge-shop-words.general`: 어떻게·방법·보관·해동·먹어도돼·괜찮·왜·언제와·언제도착·배송)이 있으면 GENERAL. "얼마나"·"가격"은 넣지 않음 ("두부 얼마나 남았어?"는 냉장고 보기)
  - `correctTastes`를 다섯 맛(`TasteWordChecker.stances`)으로
  - 냉장고 보기 범위 보충 단어(`imminent`)에 상한·상할·상했·오래된·오래됐·처리해야 (AI가 ALL일 때만 보충)
  - 요리 이름은 서버가 정함: 첫 키워드가 메시지에 그대로 있으면 그 이름 (`IntentResult.withDishFromKeywords`)
  - 묻는 말(`ask-words.question`)에 영양소·영양성분·효능
  - `correctTastes`·`keepSpokenTastes`·`nameOnly`가 15개 인자 생성자로 fridgeScope를 버리던 것을 `withTastes`·`withNamed`로 고침
- 3단계 `039f3e07` `IntentResult.dishText`에 `@JsonIgnore`: 스키마·AI 응답에서 빠지고 서버가 정한 값만 실음. 스키마 1,207 → 1,169자 (296 → 285토큰)
- 4단계 `af88e6c8` 첫 질문 프롬프트 4,905 → 2,576자 (2,762 → 1,492토큰, −46%)
  - 의도별 정의로 묶고, 서버가 하는 일(OFF_TOPIC 되살리기 예시, 맛 단어 예시, 묶음 분류 예시, 범위 보충, 칸마다 되풀이한 빈 값 안내)을 뺌
  - 역할을 정하는 말·과장한 말은 "→ GENERAL"로 적음. fridgeScope 설명 한 줄, keywords 예시 8 → 4개
- 5단계 `07367218` 후속 프롬프트 1,692 → 1,163자 (863 → 599토큰, −31%)
  - 서버가 하는 일(새 추천의 맛 비우기, 피할 맛 잇기 `mergeRefine`, 대상 칸 정리)과 첫 질문 규칙 되풀이를 뺌
  - 한 줄 규칙: "맛·시간·재료만 말한 짧은 말도 REFINE이고 [직전 조건]의 분류를 잇는다(분류와 어울리지 않아 보이는 맛이어도)"
- 입력 어림 (분류 목록 30개, o200k): 첫 질문 약 3,170 → 약 1,880토큰 (−41%), 후속 약 4,120 → 약 2,560토큰 (−38%)

## 줄인 뒤 평가 1차 (`eval-20261109-143802`)
- 전체 92/98 (93.9%). 기준값에서 틀린 5문장(R06·G06·G10·G11·G12)은 모두 맞음. 새로 틀린 6문장
- 프롬프트: 첫 질문 2,678자 / 1,585토큰(분류 목록 포함), 후속 1,163자 / 599토큰
- AI 호출 288번 · 평균 입력 1,927토큰(캐시 1,788) · 평균 출력 74토큰 · 평균 1,817ms · 최대 5,381ms (기준값 3,249토큰 · 1,969ms · 최대 6,315ms)

| 문장 | AI 원래 값 | 원인 | 처리 (6단계 보완) |
|---|---|---|---|
| R04 차가운 요리 추천해줘 (0/3) | preferenceText 빈 값, keywords는 맞음 | AI: 예시가 하나라 약함 | 프롬프트: "keywords로 바꾼 특징("차가운", "시원한")은 꼭 쓴다" + "따뜻한 국물 요리" 예시 |
| R07 따뜻한 국물 요리 (1/3) | keywords에 국·찌개·전골·수프·스튜 (분류를 옮겨 적음) | AI | 서버: 키워드가 모두 고른 분류 이름이면 키워드를 비움 (`IntentResult.sanitize`) |
| U16 (김피탕 뒤) 디저트 추천해줘 (0/3) | keywords [디저트] | AI | 서버: 같음 |
| R10 간단한 한 끼 추천해줘 (0/3) | moodCategories [밥]만 | AI: 예시가 있어도 하나만 고름 | 서버: 묶음 말 `간단한한끼`·`간단하게한끼` → 밥·면류·볶음 ("한끼"만으로는 보지 않음, "다이어트 한 끼"는 샐러드) |
| G08 장보기 목록 짜줘 (0/3) | SHOP | AI: 줄이면서 이 예시를 뺌 | 서버: `fridge-shop-words.general`에 짜줘·짜줄 (FRIDGE·SHOP → GENERAL) |
| F11 슬슬 처리해야 할 거 있어? (0/3) | GENERAL | AI | 서버: `fridge-shop-words.fridge`에 처리해야할거·처리할거 (GENERAL·RECOMMEND → FRIDGE, 범위는 `imminent`의 처리해야로 IMMINENT). "처리해야"만으로는 보지 않음 ("고기 핏물 처리해야 돼?") |

- 첫 질문 프롬프트 2,576 → 2,640자

## 연결과 남은 작업
- 실제 커밋·PR: `00de1b47` test, `77839c9a` docs, `dff33781`·`039f3e07`·`af88e6c8`·`07367218` refactor. push·PR 안 함
- 6단계: 1차 평가(92/98)의 떨어진 6문장 보완함 → 사용자가 2차 평가로 확인
- 7단계: 평가 전후표로 이 기록 마무리, api-spec 확인
