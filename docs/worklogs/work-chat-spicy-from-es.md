# 상담 후보·밀키트 맛과 매운 단계를 ES 색인 값으로 읽기

상태: 검증 대기 · 작성일: 2026-11-08 · 담당: zcookiez

브랜치 `refactor/chat-spicy-from-es`에서 한 작업이다. 커밋은 로컬에만 있고 push·PR은 하지 않았다.

## 목표와 완료 기준
- 사용자 문제:
  - 상담 후보를 고를 때마다 DB에서 맛(`findTastesByRecipeIds`)과 재료 이름(`findIngredientTexts`)을 읽어 `SpicyRecipeDetector`로 매운 정도를 다시 계산했다. 같은 값이 레시피 색인(`tastes`, `spicy_level`)에 이미 있다 (#601·#602·#603).
  - 밀키트 고르기는 연결 레시피의 DB 맛 데이터만 봐서, 맛 데이터에는 매운맛이 없지만 매운 재료가 든 레시피의 밀키트가 "매운 거 말고"에서 빠지지 않았다. 회원 5번 기준으로 27번 부추 콩가루 찜 밀키트(레시피 539, 색인 HOT)가 나왔다.
- 확인 가능한 완료 조건:
  - ES 경로에서 맛·매운 단계를 검색 결과(`CandidateHit`)에서 읽고, 맛·재료 이름 DB 조회를 하지 않는다
  - 확실히 매움은 HOT("매운 걸로"에 맞음), 매울 수 있음은 MILD 이상("매운 거 말고"에서 뺌)
  - 검색 결과가 아닌 후보(벡터 보충)는 ES에서 id로 한 번에 읽고, 실패하면 그 레시피만 DB로 계산한다
  - 밀키트도 같은 기준으로 거른다
  - 같은 조건의 상위 10개가 바꾸기 전후로 같다 (밀키트는 의도한 변화만)

## 근거와 범위
- 선행 작업: #601(맛 데이터가 매운맛이면 HOT), #602(후보 검색 결과에 tastes·spicy_level), #603(MILD 정확한 이름 고추·붉은고추·고추냉이)
- 로컬 ES 확인 (`recipes_20261107205437`, 1,693건): NONE 1,053 / HOT 466 / MILD 174. "맛 SPICY인데 HOT 아님" 0건, "HOT인데 맛 SPICY 없음" 0건
- 변경할 영역: chat(`RecipeCandidateFacade`, `MealkitPicker`, `ChatRecommendationFacade`), search(`RecipeCandidateSearchService`에 id로 읽기, `RecipeTasteLevel` DTO)
- 남긴 것:
  - DB 대체 경로(ES를 쓰지 않음·검색 실패)는 이전과 같이 DB로 계산한다
  - `SpicyRecipeDetector`: 레시피 색인(`RecipeDocumentReader`)과 DB 계산이 쓴다
  - `app.chat.candidate.spicy-*` 설정: 색인과 DB 계산이 쓴다
  - recipe 도메인 `findTastesByRecipeIds`·`findIngredientTexts`: DB 대체 경로, ES로 읽지 못한 레시피, 저장된 카드 다시 보기(`describeRecipes`, 맛만)가 계속 쓴다. 지울 것 없음

## 계획
| 단계 | 변경 | 검증 |
|---|---|---|
| 1 | ES 검색 결과의 맛·매운 단계를 후보 모음에 담아 정렬에 쓴다 | 단위 테스트, 전후 상위 10개 비교 |
| 2 | 벡터 보충 후보는 ES에서 id로 읽고 실패하면 DB로 계산 | 단위 테스트 |
| 3 | 밀키트 연결 레시피도 id로 읽어 MILD·HOT을 "매운 거 말고"에서 뺀다 | 단위 테스트, 전후 밀키트 비교 |

## AI 활용과 개발자 판단
- AI에 맡긴 작업: 선행 변경·로컬 ES 확인, 계획, 구현, 테스트, 전후 비교
- 개발자 판단:
  - 벡터 보충은 ES id 조회(방법 B)로 하고, 실패하면 그 레시피만 DB로 계산한다 (DB 계산은 조회 2번, ES id 조회는 1번이고 색인과 같은 기준)
  - 밀키트도 이번에 같이 바꾸되, 동작이 바뀌므로 `fix` 커밋으로 나눈다
- 조사에서 확인한 것:
  - 피할 맛으로 다시 채운 후보는 `collectFromEs`로 찾아 검색 결과가 있다. 따로 읽을 필요가 없다
  - 검색 결과가 없는 후보는 벡터 보충(`searchKeywordsOnce`)뿐이다
  - HOT 레시피는 색인 맛에 항상 SPICY가 있다. 이전에는 매운맛 얘기가 없으면 확실히 매운 재료로 SPICY를 보태지 않았지만, 맛 비교는 원하는 맛·회원 선호 맛·피할 맛에 SPICY가 있을 때만 SPICY를 보므로 순위는 같다 (아래 비교의 김치찌개로 확인)
- 요청에 있던 "나중 모음의 '밀키트는 레시피 맛으로만 거름'" 항목은 docs에서 찾지 못해 고치지 않았다

## 구현 결과
- `search/dto/RecipeTasteLevel` (새 파일): 색인 맛·매운 정도. `definitelySpicy()` = HOT, `maybeSpicy()` = NONE이 아님
- `RecipeCandidateSearchService`
  - `CandidateHit.tasteLevel()` 추가
  - `findTasteLevels(ids)`: ids 조건으로 한 번 검색하고 `tastes`·`spicy_level`만 받는다. 인덱스에 없는 id는 빠진다. id가 없으면 ES를 부르지 않는다
- `RecipeCandidateFacade`
  - `CandidatePool.tasteLevels`: ES 검색 결과의 맛·매운 정도. DB 경로와 벡터 보충은 비어 있다
  - `loadDetails` → `tasteDetails`
    1. 검색 결과 값을 쓴다
    2. ES 경로면 모르는 레시피를 `findTasteLevels`로 읽는다
    3. 그래도 모르면 DB로 계산한다(`tasteDetailsFromDb`: 이전과 같은 `findTastesByRecipeIds` + 매운맛이 중요하면 `findIngredientTexts`)
  - `spicyRecipes`·`withDefinitelySpicy`는 DB 계산에서만 쓴다. `spicyMatters` 판단을 따로 뺐다
  - `describeMealkitRecipes`: 같은 `tasteDetails`로 읽고 `MealkitRecipes.maybeSpicyRecipeIds`를 더해 돌려준다. ES를 쓰지 않거나 읽지 못하면 DB 맛 데이터와 재료 이름으로 매운 정도까지 계산한다 (이전에는 DB 맛 데이터만 봤다)
- `MealkitPicker.Input.maybeSpicyRecipeIds`: 피할 맛이 매운맛이면 이 레시피의 밀키트를 뺀다. 원하는 맛 매운맛은 맛에 SPICY가 있는(HOT) 것만 센다. 연결 레시피가 없는 밀키트는 이전과 같다
- `ChatRecommendationFacade`: `maybeSpicyRecipeIds`를 고르기에 넘긴다
- 문서: `docs/api/api-spec.md` 밀키트 고르기 설명에 매운 정도 거르기를 더했다
- API 요청·응답, DB, ErrorCode 변경 없음. 프론트엔드에 전할 것 없음

## 검증
| 명령·시나리오 | 실행 환경·조건 | 통과/실패/미실행 | 근거 |
|---|---|---|---|
| `./gradlew clean compileJava compileTestJava` 후 `./gradlew test` | 로컬 | 통과 | 2,170개, 실패 0, 건너뜀 1(기존 수동 테스트) |
| 전후 상위 10개·조회 수 비교 | 임시 `@SpringBootTest`, local 프로필(dev MySQL + 로컬 ES, `app.search.engine=elasticsearch`), 회원 5번, 같은 조건을 두 번 돌려 두 번째 값, 비교 후 삭제 | 실행 | 아래 표 |
| 벡터 보충 후보의 실제 ES id 조회 | 위와 같음 | 미확인 | "탕짜면"의 벡터 보충은 테스트 환경의 OpenAI 키가 가짜라 글자 검색 2건만 나왔고, 둘 다 이미 후보에 있어 새로 더한 레시피가 0건이었다. 단위 테스트로만 확인. `findTasteLevels` 자체는 밀키트 경로에서 실제 ES로 확인됨 |

**전후 비교** (회원 5번, SQL은 Hibernate `prepareStatementCount`, 냉장고·회원 조회 포함)

| 조건 | SQL 전 → 후 | 상위 10개 |
|---|---|---|
| 매운 거 추천해줘 | 6 → 4 | 같음 |
| 매운 거 말고 | 8 → 6 | 같음 |
| 매운 거 말고 새우겨자채 (106, 맛 데이터로만 매움) | 8 → 6 | 같음 (106 콕 집어 남김) |
| 매운 걸로 새우겨자채 | 6 → 4 | 같음 |
| 매운 거 말고 고추잡채 (59, 고추만 든 MILD) | 8 → 6 | 같음 (1547·59 콕 집어 남김) |
| 매운 걸로 고추잡채 | 6 → 4 | 같음 (59는 원하는 맛으로 안 셈) |
| 김치찌개 | 7 → 6 | 같음 |
| 매운 거 말고 탕짜면 (벡터 보충) | 8 → 6 | 같음 |
| 매운 거 말고 밀키트 추천해줘 | 9 → 8 | 전 24·27·26 → 후 24·26·28 (27 부추 콩가루 찜, 레시피 539 HOT이 빠짐) |

- 후보 한 번에 줄어든 DB 조회: 매운맛 얘기가 있으면 2번(맛 + 재료 이름), 없으면 1번(맛)
- 밀키트: DB 맛 1번이 ES id 조회 1번으로 바뀌었다

## 연결과 남은 작업
- 실제 커밋:
  - `refactor: 상담 후보 맛·매운 단계를 ES 검색 결과에서 읽기`
  - `refactor: ES 결과가 아닌 후보의 맛·매운 단계 읽기`
  - `fix: 밀키트도 매운 단계로 피할 맛 거르기`
  - `docs: 작업 기록`
- PR: 없음 (push하지 않음)
- 미검증·결정 대기 항목:
  - 벡터 보충으로 새 레시피가 들어오는 경우의 실제 ES id 조회 (단위 테스트만)
  - 저장된 추천 카드 다시 보기(`describeRecipes`)는 이번 범위가 아니라 DB 맛 데이터만 쓴다. 이 맛은 카드 이유 계산에만 쓰이고 거르기에는 쓰이지 않는다
  - `CandidateHit.definitelySpicy()`는 맛 SPICY 기준이고 chat은 `RecipeTasteLevel`의 HOT 기준을 쓴다. 색인에서 둘이 같게 맞춰져 있어 결과는 같다. search 쪽 메서드는 손대지 않았다
