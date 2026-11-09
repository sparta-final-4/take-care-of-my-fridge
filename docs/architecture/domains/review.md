# 후기 설계

상태: 구현 완료 · 담당: 이현정 · API: [후기 API](../../api/api-spec.md#후기-api)

## 목적과 책임
- 해결할 사용자 문제: 직접 만들어 본 레시피에 별점과 짧은 후기를 남기고, 다른 사람의 후기를 본다.
- 이 도메인이 소유하는 데이터: 레시피 후기(`recipe_reviews`), 후기 요약 작업 결과.
- 다른 도메인에 맡기는 책임: 레시피 존재 확인과 팁 요약 저장(레시피), 요리 완료 여부(요리 기록), 닉네임(회원).

## 동작 규칙
| 기능 | 규칙 | 근거 |
|---|---|---|
| 목록 | 비로그인도 봅니다. `mine=true`는 로그인이 필요합니다(401 `A003`). 항목마다 로그인한 사람이 쓴 글인지(`mine`)를 붙이고, 레시피 이름은 한 번에 모아 읽습니다 | `RecipeReviewService.getReviews` |
| 작성 확인 순서 | 레시피 있음(404 `RECIPE_001`) → 요리 완료 기록 있음(403 `REVIEW_003`) → 이미 쓴 후기 없음(409 `REVIEW_002`) | `RecipeReviewService.createReview` |
| 1회원 1레시피 1후기 | 동시에 써서 유니크 제약(`uk_recipe_reviews_member_id_recipe_id`)에 걸려도 409 `REVIEW_002`로 바꿉니다 | `RecipeReviewService.createReview` |
| 본인 후기 | 수정·삭제는 본인만 합니다. 남의 후기와 없는 후기는 구분하지 않고 404 `REVIEW_001`입니다 | `RecipeReviewService` |
| 후기 요약 | 매일 정해진 시각(`app.review-summary.cron`, 기본 04:00)에 돌고, 관리자가 바로 돌릴 수도 있습니다. 대상은 후기 5개 이상이면서 팁 요약이 없거나 최근 후기가 요약보다 새로운 레시피, 최근 후기 순 100개까지입니다. 레시피마다 최근 후기 50개로 AI 요약(목표 120자, 150자 넘으면 문장 끝에서 자름)을 만들어 팁 요약에 저장합니다 | `ReviewSummaryFacade` |
| 요약 실행 제한 | 한 번에 하나만 돕니다(ShedLock `reviewSummary`, 이미 돌면 409 `REVIEW_004`). 관리자 실행은 20초까지만 하고 남은 레시피는 SKIPPED로 다음에 넘기며, AI가 429면 그 레시피는 FAILED, 나머지는 SKIPPED로 멈춥니다. AI 호출은 트랜잭션 밖에서 합니다 | `ReviewSummaryAdminController`, `ReviewSummaryFacade.runOnce` |
