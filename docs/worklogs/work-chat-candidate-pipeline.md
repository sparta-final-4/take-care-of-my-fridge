# WORK-CHAT-CANDIDATE-PIPELINE: 상담 후보 고르기 코드 정리 (3단계)

상태: 완료 · 작성일: 2026-11-16 · 브랜치: `refactor/chat-candidate-pipeline`

앞 단계: [요리 이름 검색 바로잡기(0·1단계)](work-chat-dish-name-search.md), [비슷한 요리 넣기(2단계)](work-chat-similar-dishes.md).
기준 기록: [상담 레시피 검색 기준 기록](../testing/chat-recipe-search-baseline.md).

## 목표와 완료 기준
- 사용자 문제: 두 단계를 거치며 후보 고르기(`RecipeCandidateFacade`)에 단계가 덧대져 단계마다 "합치기 → 정보 읽기 → 정렬"이 되풀이되고, 같은 판정이 두 곳에 있었다. 동작은 그대로 두고 코드를 줄인다.
- 완료 조건:
  - 채팅 main 코드 12,662 → 12,625 이하, `RecipeCandidateFacade` + `RecipeCandidateCollector` 962 → 930 이하.
  - 동작을 확인하는 기존 테스트는 고치지 않고 통과. 고치는 테스트는 `KeywordMatchClassifierTest`의 오버로드 호출뿐.
  - 로그 문구를 확인하는 `suppliesVector_whenEsKeywordHitsAreWeak`, `ChatIntentReplayTest` 그대로 통과.

## 근거와 범위
- 변경할 영역: `domain/chat/facade/RecipeCandidateFacade`, `domain/chat/recommend/KeywordMatchClassifier`, 테스트 2개.
- 다른 도메인 영향: 없음. search 도메인(ES 쿼리)은 바꾸지 않았다.
- 하지 않은 것(사용자 결정):
  - 피할 맛을 ES boosting으로 바꿔 재검색 없애기: search 도메인 변경이라 하지 않음.
  - `CandidatePool` 로그용 칸(`route`, `counts`, `esCalls`, `vectorCalled`)과 로그 문구 정리: 감사 페이지에서 쓸 예정이라 그대로 둠.
  - 이름 조각 검색 합치기, 요청만 2차 검색 없애기, DB 대체 경로 없애기, SPLIT 처리 걷어내기, `SimilarDishFinder`·`SimilarDishStore` 합치기.

## 계획
| 단계 | 변경 | 검증 |
|---|---|---|
| 0 | T4 테스트 추가: 비슷한 요리·키워드 보충(벡터)·이름 조각을 모두 거친 경우의 맞은 방식과 순서 | 지금 코드에서 통과 |
| 1 | 단계 공통 합치기를 도우미(`merge`)로, 중간 단계는 맞은 방식만 가르고(`classified`) 정렬은 끝에서 한 번 | chat 테스트 |
| 2 | 쓰지 않는 `classify` 오버로드 제거, 겹치는 strong 판정 하나로, 끝의 못 찾음 조립 정리 | chat 테스트 |
| 3 | 이 기록, 줄 수 다시 측정 | clean compile, 전체 test |

## AI 활용과 개발자 판단
- AI에 맡긴 작업: 줄일 후보 조사·계획, 위 0~2단계 구현, 측정.
- 채택: 계획 중 A(합치기·정렬 도우미)와 E-2(겹치는 코드)만. 사용자가 B·G·H 등은 하지 않기로 정했다.
- 개발자가 확인할 근거: 단계별 커밋, 아래 검증 표.

## 구현 결과
- `RecipeCandidateFacade`
  - `Collected`가 정렬 결과(`Ranked`) 대신 맞은 방식을 가른 검색 조건(`query`)과 `knownAvoided`를 들고 다닌다. `Ranked` record는 없앴다.
  - `classified`(맞은 방식 가르기)와 `merge`(합치기 + 새 레시피 정보·맛 읽기 + 다시 가르기 + 로그용 경로·수 붙이기)를 두고, 비슷한 요리·키워드 보충·이름 조각·피할 맛 재검색이 이것을 쓴다.
  - 정렬(`rankPool`)은 `findCandidates`에서 한 번 한다. 피할 맛 재검색(`refillAvoided`)은 정렬 결과가 3개 미만일 때만(`needsAvoidedRefill`) 하고, 새로 채운 게 있으면 그때만 다시 정렬한다.
  - `RankInput` record와 `rankAvoiding`을 `rankPool` 하나로 합쳤다(정렬 값은 `Collected`·`Context`에서 바로 읽음, `ScoringContext`는 한 번 만든다).
  - `hasStrongCandidate`와 `hasStrongKeywordMatch`를 `anyStrong` 하나로. 요리 이름 못 찾음(`dishNotFound`)은 정렬 결과로 정하고 `toResult`에서 표시한다(결과를 다시 만들지 않음).
- `KeywordMatchClassifier`: 이름 조각 없이 가르는 5개 인자 `classify` 오버로드 제거(main에서 쓰지 않았음).
- 로그 문구는 그대로다(`상담 후보 모으기 - … 경로: …, ES 호출 전체 …·요청만 …·피할 맛 재검색 …, 벡터 …`).
- 정리 커밋에서는 키워드 보충이 새 레시피를 하나도 못 찾으면 맞은 방식을 다시 가르지 않는 동작을 그대로 두었고, 뒤 커밋에서 고쳤다(아래 "남은 작업" 참고).

### 줄 수

측정 명령(`take-care-of-my-fridge-be/src/main/java/org/example/takecareofmyfridgebe/domain/chat`에서):
```bash
find . -name '*.java' | xargs cat | wc -l
wc -l facade/RecipeCandidateFacade.java facade/RecipeCandidateCollector.java service/SimilarDish*.java facade/SimilarDishFinder.java recommend/*.java
```

| 시점 | 커밋 | chat main | Facade | Collector | Facade+Collector | recommend |
|---|---|---|---|---|---|---|
| 브랜치 시작 | `d1878eb1` | 12,662 | 494 | 468 | 962 | 1,355 |
| 0단계 (테스트만) | `9b7c8ad3` | 12,662 | 494 | 468 | 962 | 1,355 |
| 1단계 | `773e62aa` | 12,638 (−24) | 470 | 468 | 938 | 1,355 |
| 2단계 | `a4b5358b` | 12,624 (−14) | 462 | 468 | 930 | 1,349 |

합계 −38줄 (목표 −37), Facade+Collector −32줄 (목표 −32). `SimilarDishStore` 77, `SimilarDishFinder` 74는 그대로다.

### 바꾼 테스트
- 추가: `RecipeCandidateFacadeTest.ordersExpandedFragmentAndVectorHits_whenAllSupplementsRun` (T4).
- 고침: `KeywordMatchClassifierTest`의 5개 인자 호출 5곳(계획 보고에는 4곳으로 셌다)을 6개 인자(조각 `List.of()`) 호출로.
  `classifiesIngredient_whenSameName`, `classifiesIngredient_whenKeywordInsideIngredientName`, `classifiesSplit_whenOnlyPartOfKeywordOverlaps`, 도우미 `classify(…)`(`classifiesName`·`classifiesExpanded`·`classifiesSplitOrVector`·`ignoresOneLetterOverlap` 등이 씀), `classifiesName_whenDishTextHasNewline`. 기대값은 바꾸지 않았다.

## 검증
| 명령·시나리오 | 실행 환경·조건 | 통과/실패/미실행 | 근거 |
|---|---|---|---|
| `./gradlew test --tests '*.domain.chat.*'` (0·1·2단계 끝마다) | 로컬, Docker 실행 중 | 통과 | 869건, 실패 0, 건너뜀 2 (단계마다 같음) |
| `./gradlew clean compileJava compileTestJava` | 로컬 | 통과 | BUILD SUCCESSFUL, 미사용 import 없음 |
| `./gradlew test` | 로컬, Docker 실행 중 | 통과 | 2,426건, 실패 0, 건너뜀 3. `PopularRecipeServiceCacheIntegrationTest`도 통과 |
| `ChatIntentReplayTest` | 전체 test 안에서 | 통과 | 기준값 다시 기록하지 않음 |
| `suppliesVector_whenEsKeywordHitsAreWeak` (로그 문구) | chat 테스트 안에서 | 통과 | 고치지 않음 |
| 실제 ES·AI로 후보 비교 | — | 미실행 | 동작을 바꾸지 않는 정리라 단위 테스트로만 확인 |

## 연결과 남은 작업
- 실제 커밋: `9b7c8ad3`(테스트), `773e62aa`(합치기·정렬 도우미), `a4b5358b`(겹치는 판정 제거), 이 기록. push는 하지 않았다.
- 남은 일:
  - 감사 페이지 작업 때 `CandidatePool` 로그용 칸(`route`, `counts`, `esCalls`, `vectorCalled`)을 문자열 대신 기록용 record로 바꾸고 구조화된 로그(JSON 등)로 남기기.
  - 키워드 보충이 이미 모은 레시피만 찾으면(새 레시피 0건) 그 레시피의 키워드 결과 표시가 맞은 방식에 반영되지 않는다. `foundPool`은 표시하라고 되어 있어(요리 이름 추천은 키워드로 찾은 후보만 카드에 쓰므로) 의도와 다르다. 예: "김치찌게"를 벡터로 찾은 김치찌개가 이미 냉장고 점수로 후보에 있으면 카드에서 빠질 수 있다. 정리 커밋에서는 동작을 지키려고 그대로 두었다(`supplementKeywords`의 새 레시피 0건 분기).
    - (해결) 그 분기를 없애고 키워드 보충 결과를 늘 합쳐 맞은 방식을 다시 가른다. 비슷한 요리 검색처럼(`foundPool`) 이미 모은 후보를 다시 찾으면 키워드 결과(벡터로만 찾았으면 VECTOR)로 표시돼 요리 이름 추천 카드에 남는다. 테스트: `RecipeCandidateFacadeTest.keepsPooledRecipeAsVector_whenVectorSupplementFindsItAgain`(고치기 전 코드에서는 실패). 기존 테스트는 고치지 않았다.
  - 계획에서 하지 않기로 한 것: 피할 맛 boosting(search 도메인), 이름 조각 검색 합치기, 요청만 2차 검색, DB 경로.
