# AI 상담 의도 판단 프롬프트 줄이기

상태: 진행 (1단계 평가 세트까지) · 작성일: 2026-11-09 · 담당: zcookiez

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
| 기준값 (줄이기 전) | 실제 OpenAI 키 | 미실행 | 사용자가 다시 돌림 |

## 연결과 남은 작업
- 실제 커밋·PR: 1단계 커밋 후 기준값 대기
