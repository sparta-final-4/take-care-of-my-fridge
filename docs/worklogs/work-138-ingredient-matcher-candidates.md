# WORK-138: 재료 매칭에 후보 검색(findCandidates) 연결

상태: 검증 대기 · 작성일: 2026-11-02 · 담당: zcookiez

## 목표와 완료 기준

- 냉장고 등록 때 DB에 같은 이름이 없는 재료명도 표준 재료로 매핑한다("계란" → 달걀, "서울우유" → 우유).
- 다른 음식을 잘못 잡지 않는다("마늘빵", "콜라", "새우깡", "파"는 기타).
- 후보 검색 장애여도 등록은 계속된다.

## 근거와 범위

- 선행: `IngredientNameSearchService.findCandidates`(ES nori + 별칭, 점수 0~1).
- 점수 하나로는 기준을 잡을 수 없어(콜라 → 루콜라 0.82, 국산 흙대파 → 대파 0.48) 규칙 두 개로 채택한다.

## 구현 결과

- `IngredientMatcher` 매칭 순서(match·matchAll 같음): 다듬기 → DB 같은 이름 일괄 조회 → 못 찾은 이름만 `findCandidates` →
  a. 1등 점수 ≥ `app.ingredient.exact-match-score`(1.0)면 채택
  b. 아니면 입력 이름(공백 제거)이 후보 이름으로 끝나는 후보 중 최고점 채택(후보 이름 `app.ingredient.suffix-min-length` 2글자 이상)
  → 채택한 이름들을 `findByNameIn`으로 한 번에 다시 조회 → 없으면 ING_CUSTOM.
- 후보 검색 예외는 WARN 로그 후 기타로 등록(예외를 밖으로 내보내지 않음). 검색 엔진을 쓰지 않는 설정이면 `findCandidates`가 빈 목록이라 기타.
- `IngredientMatchProperties`(새로 만듦, `app.ingredient.*`), `application.yml`에 값 추가.
- 주석: `IngredientMatcher` TODO 제거·클래스 주석에 순서와 끝부분 규칙 이유, `IngredientNameSearchService`·`FridgeFacade`의 오래된 주석 갱신.
- API·DTO·ErrorCode·DB 변경 없음.

## 검증

| 명령·시나리오 | 실행 환경·조건 | 통과/실패/미실행 | 근거 |
|---|---|---|---|
| `./gradlew clean test` | 로컬, Testcontainers | 통과 | 1329건, 실패·오류 0, 건너뜀 1(기존 수동 테스트 `IngredientNameSearchServiceManualTest`) |
| `IngredientMatcherTest` | 단위, findCandidates 목(측정 점수) | 통과 (20건) | 계란→달걀, 서울우유→우유, 국산 흙대파→대파, 꿀참외→참외, 마늘빵·콜라·새우깡·파→기타, DB에 있으면 후보 검색 안 함, 못 찾은 이름만 검색·채택 이름 일괄 재조회, ES 예외→기타 |
| 실제 ES·DB로 냉장고 등록 | 로컬 서버 | 미실행 | 없음 |

## 연결과 남은 작업

- 실제 커밋·PR: 아직 없음
- 확인 필요: 도메인 간 의존(ingredient 서비스 → search 서비스), 이름마다 ES 요청, BM25 점수 1.0 상한, 끝부분 규칙의 맛 우유류 등(작업 보고 참고)
