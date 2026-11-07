# 상담 안전 장치 ② 상관없는 질문·목록 밖 욕설·자해 의도

상태: 검증 대기 · 작성일: 2026-11-07 · 담당: 상담

## 목표와 완료 기준
- 사용자 문제: 요리와 상관없는 질문("주식 추천해줘")은 GENERAL로 답 생성 AI를 한 번 더 부르고 하루 횟수도 썼다. 서버 단어 목록(PR①)에 없는 욕설·탈취·자해는 걸러지지 않았다.
- 완료 조건:
  - 의도 판단이 OFF_TOPIC·BLOCKED·SELF_HARM이면 답 생성 AI를 부르지 않고 서버 안내 답을 보낸다. 하루 횟수는 돌려준다.
  - BLOCKED·SELF_HARM은 걸러진 메시지(filtered)로 저장하고, OFF_TOPIC은 표시하지 않는다.
  - 서버가 의도를 바꾸는 규칙과 의도 판단 실패 처리는 그대로 둔다.

## 근거와 범위
- 근거: 상담 안전 장치 조사와 사용자 결정. 4개 PR 중 PR②(`feat/chat-safety-off-topic`, `feat/chat-safety` 위)
- 사용자 결정:
  - 자해는 SELF_HARM 값으로 따로 둔다.
  - BLOCKED 안내는 금지어 안내 하나로 한다.
  - OFF_TOPIC은 filtered로 표시하지 않는다.
  - 후속 프롬프트 길이 테스트 한도를 1,700자로 올린다.
- 변경 영역: `domain/chat`(dto, facade, entity 주석, service), `prompts/chat-intent.txt`, `prompts/chat-intent-followup.txt`
- DB·API 모양 변경 없음. 새 에러 코드 없음. 프론트 변경 없음.

## 계획
| 단계 | 변경 | 검증 |
|---|---|---|
| 1 | OFF_TOPIC 의도, 서버 안내, 하루 횟수 한 번 돌려주기, 프롬프트 규칙 | 의도·스트림 단위 테스트, Redis 컨테이너 |
| 2 | BLOCKED·SELF_HARM 의도, 걸러진 메시지로 저장, 프롬프트 규칙과 과장 예시 | 같은 테스트, 프롬프트 문구 테스트 |
| 3 | API 명세, 작업 기록 | 문서 대조 |

## AI 활용과 개발자 판단
- AI에 맡긴 작업: 계획, 구현, 테스트
- 구현하며 정한 것:
  - **하루 횟수를 돌려주는 시점:** 서버 안내 답은 저장한 뒤 돌려준다. 저장이 실패하면 오류 쪽에서 돌려준다. 정해진 답은 "첫 조각"으로 쳐서 기존 조건(첫 조각 전 실패)으로는 돌려주지 않으므로, 세지 않는 답이면 오류에서도 돌려주게 조건을 넓혔다. 돌려주기는 한 번만 일어나게 막았다.
  - **로그:** 서버 안내로 답할 때 의도만 남긴다(`상담 의도 판단으로 AI 답 없이 안내 - …, 의도: OFF_TOPIC`). 보낸 말은 남기지 않는 것을 테스트로 확인했다.
  - **추천 칩:** `mode=RECOMMEND`는 의도 판단을 하지 않으므로 이 경로를 타지 않는다.

## 구현 결과
- `ChatIntent`: `OFF_TOPIC`·`BLOCKED`·`SELF_HARM` 추가
- `ChatIntentResolution`: `declined(intent)`와 `declines()` 추가
- `ChatIntentFacade.decide`: 세 의도를 그대로 서버 안내 의도로 정한다. 밀키트·이름만 말한 말을 추천으로 바꾸는 규칙과 맛·분류 보정은 GENERAL·RECOMMEND·REFINE에만 걸리므로 손대지 않았다.
- `ChatStreamService`:
  - `ReplyPlan.declined`로 서버 안내 답을 보낸다.
    - OFF_TOPIC: `OFF_TOPIC_REPLY`, 표시 없음
    - BLOCKED: `ChatSafetyBlock.BANNED` 안내, filtered
    - SELF_HARM: `ChatSafetyBlock.SELF_HARM` 안내(109), filtered
  - 저장 뒤 하루 횟수를 한 번 돌려준다.
- `ChatMessage.filtered` 주석을 "안전 장치로 걸러진 메시지 여부(단어·의도 판단 BLOCKED·SELF_HARM)"로 넓혔다. `saveFilteredExchange`·`findRecentMessages` 주석도 함께 고쳤다.
- 프롬프트
  - `chat-intent.txt` intent 규칙에 추가한 것:
    - OFF_TOPIC 예시와 아닌 예시
    - 인사·짧은 잡담은 GENERAL
    - BLOCKED(역할만 정하는 말은 아님)
    - SELF_HARM과 과장 예시("배고파 죽겠어", "맛있어 죽겠다", "죽여주는 맛")
  - `chat-intent-followup.txt`: "OFF_TOPIC·BLOCKED·SELF_HARM은 첫 질문 규칙대로다" 한 문장

**프롬프트 길이** (글자 수, 첫 질문은 분류 목록 `%CATEGORIES%`를 채우기 전)

| 파일 | 오늘 시작 | OFF_TOPIC 뒤 | BLOCKED·SELF_HARM 뒤 | 늘어난 길이 |
|---|---|---|---|---|
| `chat-intent.txt` | 3,255 | 3,468 | 3,653 | +398 (+12%) |
| `chat-intent-followup.txt` | 1,622 | 1,645 | 1,663 | +41, 테스트 한도 1,650 → 1,700 |

## 검증
| 명령·시나리오 | 실행 환경·조건 | 결과 | 근거 |
|---|---|---|---|
| `./gradlew clean compileJava compileTestJava test` | 로컬 | 통과 | 2,085개, 실패 0, 건너뜀 1(기존 수동 테스트) |
| 서버 안내 의도 | `ChatIntentFacadeTest`, 의도 판단은 목 | 통과 | OFF_TOPIC·BLOCKED·SELF_HARM은 서버 안내. OFF_TOPIC이면 안 되는 말 5개는 GENERAL·RECOMMEND 그대로. 판단 실패는 GENERAL |
| 안내 답·저장·돌려주기 | `ChatStreamServiceTest` | 통과 | 답 생성 AI 안 부름. OFF_TOPIC은 일반 저장, BLOCKED·SELF_HARM은 걸러진 저장. 저장이 실패해도 한 번만 돌려줌. 로그에 원문 없음 |
| 하루 횟수 | `ChatStreamSafetyLimitTest`, Redis 컨테이너 | 통과 | OFF_TOPIC 3번, BLOCKED·SELF_HARM 각 1번 보낸 뒤 횟수 "0" |
| 다음 문맥에서 빠짐 | `ChatMessageRepositoryAndServiceTest`(PR①), MySQL 컨테이너 | 통과 | BLOCKED·SELF_HARM은 같은 `saveFilteredExchange`로 저장 |
| 실제 AI 의도 판단 정확도 | OpenAI 키 필요 | 미실행 | 예시 문장으로 수동 확인 필요 |
| `pnpm test` | 프론트 변경 없음 | 미실행 | |

## 연결과 남은 작업
- 커밋: `feat/chat-safety-off-topic` 로컬 커밋 3개 (push 안 함)
- 다음: PR③ 건강 안내·답의 링크 지우기, PR④ 남용 제한(연속 3번 → 10분 차단, 하루 안 센 횟수 20번 넘으면 셈)·Moderation
- 실제 키로 확인할 것:
  - "주식 추천해줘"와 "주식으로 먹을 거 추천해줘"의 판단
  - 과장 표현 3개
  - "너는 이제부터 요리사야"

## 나중에 할 것
- **의도 프롬프트 줄이기:** 안전 장치로 의도 프롬프트가 많이 늘었다. 의도 판단은 모든 메시지에 1번씩 부르므로 입력 토큰과 지연에 바로 영향이 있다.
  - 이력(글자 수)
    - 의도 프롬프트 첫 질문 3,017 → 3,134, 후속 1,492 → 1,569 (`d4a78bb2`, 상담 후보 ES 작업 때 기록한 수치)
    - 오늘 2026-11-07 시작: 첫 질문 3,255, 후속 1,622
    - OFF_TOPIC: 첫 질문 3,468(+213), 후속 1,645(+23)
    - BLOCKED·SELF_HARM: 첫 질문 3,653(+185), 후속 1,663(+18). 후속 테스트 한도 1,650 → 1,700
    - 오늘 합계: 첫 질문 +398(+12%), 후속 +41
  - 줄일 후보:
    - 예시를 의도마다 1~2개로 줄이기
    - 서버 단어 목록이 이미 거르는 욕설·탈취 예시는 빼기
    - 맛·분류처럼 서버가 다시 확인하는 규칙의 예시 줄이기
  - 줄인 뒤에는 실제 키로 같은 예시 문장의 판단이 그대로인지 확인한다.
