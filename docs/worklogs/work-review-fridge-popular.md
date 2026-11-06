# 리뷰 반영: 인기 레시피 캐시와 구매 확정 냉장고 입고

상태: 검증 대기 · 작성일: 2026-11-06 · 담당: zcookiez

브랜치 `fix/review-fridge-popular`. 커밋은 로컬에만 있고 push하지 않았다.

## 리뷰 4. 인기 레시피 캐시 (PopularRecipeService#getPopularRecipes)

- 지적: 인기 목록을 캐시하는데 레시피가 수정되면? 24시간 늦어도 되는 정책인가?
- 결정: 레시피 삭제는 없지만 수정은 있고 이름·이미지가 바뀔 수 있으니 바로 반영한다.
- 조사:
  - 전에는 응답 전체(레시피 id·이름·이미지·팁 요약·만든 회원 수·평점·후기 수)를 Redis `recipe:popular`(키 size, TTL 24시간)에 담았다. 첫 요청에 채우고 스케줄러는 없다.
  - 레시피 수정(`RecipeChangedEvent`, 커밋 뒤 색인만 받음) 말고도 팁 요약 새벽 배치(`RecipeService.updateTipSummary`)와 후기 작성·수정·삭제가 이벤트 없이 값을 바꾼다. 이벤트로 캐시를 지우는 방법(A)은 세 군데를 챙겨야 하고 둘은 이벤트부터 만들어야 했다.
- 반영 (B, 순위만 캐시):
  - `PopularRecipeRankingService.getRanking(size)`: 최근 30일 요리한 회원 수 순위(레시피 id + 회원 수)만 Redis `recipe:popular-ranking`에 24시간 캐시한다. 순위는 30일 집계라 최대 24시간 늦게 바뀌어도 되는 정책으로 둔다. 빈 순위는 캐시하지 않는다.
  - `PopularRecipeService.getPopularRecipes`: 순위를 받아 레시피 이름·이미지·팁 요약(`findSummaries`)과 평점·후기 수(`findRatingStats`)를 요청마다 읽는다. 레시피 수정·팁 요약·후기가 바로 반영된다.
  - 캐시 이름을 바꿔 배포 뒤 예전 `recipe:popular` 값(응답 전체)을 읽지 않는다. 예전 키는 TTL로 사라진다.
- 테스트 중 발견: 캐시 테스트의 `Cache.clear()`는 Redis에서 바로 지워지지 않을 수 있어(Spring 문서상 비동기·지연 가능), 앞 테스트의 값이 남았다. `invalidate()`로 바꿨다.

### 응답 시간 (로컬 컨테이너 기준)
- 환경: 로컬 PC(Windows, Docker) Testcontainers MySQL 8.4 + Redis 7, 같은 JVM에서 `getPopularRecipes(6)` 호출. 레시피 1,600개, 회원 300명, 최근 30일 요리 기록 20,000건, 후기 5,000건. 워밍업 50번 뒤 300번 잼. 운영 환경 수치가 아니며 실행마다 몇 ms씩 흔들린다.

| 경우 | DB 쿼리 | 평균 | p50 | p95 |
|---|---|---|---|---|
| 캐시 없음 (집계 + 레시피 + 평점) | 3 | 21.05ms | 16.53ms | 43.30ms |
| 전 캐시 (응답 전체, Redis만) | 0 | 1.21ms | 0.81ms | 2.40ms |
| B 캐시 (순위만, 레시피·평점 매번) | 2 | 11.33ms | 7.22ms | 36.63ms |
| B 캐시 + `recipe_reviews(recipe_id)` 인덱스 | 2 | 6.55ms | 4.40ms | 20.40ms |

- B는 바로 반영되는 대신 요청마다 쿼리 2번이 늘어 전 캐시보다 느리고, 캐시 없음보다는 빠르다(평균 약 절반).
- 평점 집계(`findRatingStatsByRecipeIds`)가 `recipe_reviews` 전체를 훑는다(EXPLAIN type=ALL). 유니크 인덱스가 `(member_id, recipe_id)`라 recipe_id로 시작하는 인덱스가 없다. 인덱스를 두면 range 조회(rows 5,137 → 114)로 바뀐다. 후기 테이블은 이번 범위가 아니라 제안만 한다.
- 측정 테스트는 커밋하지 않았다.
