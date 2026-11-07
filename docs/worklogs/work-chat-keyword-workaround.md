# 상담 키워드 이름표 우회 정리

상태: 완료 · 작성일: 2026-11-07 · 담당: 상담

## 목표와 완료 기준
- 사용자 문제: 레시피 후보 검색이 키워드마다 같은 `_name`("keyword")을 붙여서, ES가 마지막 키워드로 맞은 레시피만 키워드 결과로 표시했다. 상담 쪽은 "이름에 키워드가 통째로 있으면 키워드 일치"로 우회했다. 레시피 담당 수정(#599)이 dev에 머지돼 이 우회를 정리한다.
- 완료 조건:
  - 로컬 ES에서 "김피탕·탕수육·깐풍기" 키워드 검색의 키워드 결과 레시피에 모두 `matched_queries: ["keyword"]`가 온다.
  - `_name` 중복 때문에 넣은 우회만 걷어내고, 걷어낸 뒤 "김피탕 같은 메뉴"·"탕짜면" 결과가 이전과 같다.
  - `./gradlew clean test` 통과

## 근거와 범위
- 근거: #599 `refactor(be): 레시피 후보 검색 키워드 조건을 named query 하나로 묶기`, [상담 후보 ES 작업 기록](work-chat-candidates-es.md)의 "알려진 문제"
- 변경 영역: `domain/chat/facade` (`RecipeCandidateFacade`, `KeywordMatchClassifier` 주석), 테스트
- 남기는 것 (분석기 한 글자 쪼개짐은 그대로라서):
  - 벡터 보충 판단에서 쪼개짐(SPLIT)은 맞은 것으로 보지 않기
  - `KeywordMatchClassifier`의 쪼개짐 분류

## 계획
| 단계 | 변경 | 검증 |
|---|---|---|
| 1 | 없음 | 로컬 ES에 #599 모양 쿼리로 `matched_queries` 확인 |
| 2 | 걷어내기 전 결과 저장 | 임시 `@SpringBootTest`로 두 조건 실행 |
| 3 | `classifyKeywordHits`의 이름 맞추기 블록 삭제, 주석 정리 | 같은 조건으로 다시 실행해 비교 |
| 4 | 우회를 검증하던 단위 테스트를 새 동작으로 바꿈 | `./gradlew clean test` |

## AI 활용과 개발자 판단
- AI에 맡긴 작업: ES 확인, 우회 삭제, 전후 비교 실행, 테스트 수정
- 요청의 "#PR번호"는 dev 기록에서 이름표 수정 커밋인 #599로 정리했다.
- `KeywordMatchClassifier`의 `[이슈 번호]`는 분석기 쪼개짐에 대한 것이라 #599로 바꾸지 않았다. "분석기는 아직 그대로다. #599는 _name만 고쳤다"로 바꿨다.

## 구현 결과
- `RecipeCandidateFacade.classifyKeywordHits`: 키워드 결과로 표시되지 않은 후보도 이름이 맞으면 키워드 일치로 넣던 블록을 지웠다. 이제 ES가 키워드로 표시한 후보만 분류한다.
- `RecipeCandidateFacade.findCandidates` 벡터 보충 주석: `_name` 문제는 #599로 고쳐졌다고 적고, 남은 이유는 분석기 쪼개짐만 둔다. 로직은 바꾸지 않았다.
- `KeywordMatchClassifier`: 주석의 `[이슈 번호]`만 정리했다.
- `RecipeCandidateFacadeTest`: `countsNameMatch_whenEsDidNotMarkKeyword`(우회 검증)를 `countsOnlyEsMarkedKeywordHits`로 바꿨다. 이제 ES가 표시한 탕수육은 늘린 이름으로 맞고, 표시 없이 온 황태탕수육은 키워드로 세지 않는지 확인한다.

API·DB·프론트 변경 없음.

## 검증
| 명령·시나리오 | 실행 환경·조건 | 결과 | 근거 |
|---|---|---|---|
| ES `matched_queries` | 로컬 ES `recipes`(1,693건), 키워드 김피탕·탕수육·깐풍기 | 확인 | 아래 표 |
| 걷어내기 전후 비교 | 임시 `@SpringBootTest`, local 프로필(dev MySQL + 로컬 ES), 회원 5번, 비교 후 삭제 | 같음 | 아래 표 |
| `./gradlew clean compileJava compileTestJava test` | 로컬 | 통과 | 2,040개, 실패 0, 건너뜀 1(기존 수동 테스트) |
| 벡터 보충 실제 동작 | OpenAI 키가 가짜 값 | 미실행 | 두 조건 모두 벡터 보충을 부르지 않아 비교에는 영향 없음 |

**ES `matched_queries`** (같은 키워드, 수정 전 모양은 키워드마다 `_name: keyword`)

| 레시피 | #599 모양 | 수정 전 모양 |
|---|---|---|
| 탕수육(433) | keyword | 없음 |
| 황태탕수육(722) | keyword | 없음 |
| 가지 탕수육(1132) | keyword | 없음 |
| 깐풍기(366) | keyword | keyword |

김피탕 이름의 레시피는 없다.

**걷어내기 전후 결과** (상위 10개, 순서·맞은 방식까지 비교)

| 조건 (키워드) | 결과 |
|---|---|
| "김피탕 같은 메뉴" (김피탕·탕수육·깐풍기) | 같음. 깐풍기·탕수육·황태탕수육·가지 탕수육이 [늘린 이름], 나머지 6개는 키워드 없음. 경로 ES+요청만, 벡터 안 함 |
| "탕짜면" (탕짜면·탕수육·짜장면) | 같음. 탕수육 3개가 [늘린 이름], 나머지 7개는 키워드 없음. 경로 ES+요청만, 벡터 안 함 |

키워드는 AI 대신 직접 넣었다. 실제 AI가 뽑는 키워드와 다를 수 있다.

## 연결과 남은 작업
- 커밋: `refactor/chat-keyword-workaround` 로컬 커밋 (push 안 함)
- 남은 우회: 분석기 한 글자 쪼개짐("탕짜면" → "짜")이 고쳐지면 벡터 보충의 쪼개짐 제외를 걷어낸다.
