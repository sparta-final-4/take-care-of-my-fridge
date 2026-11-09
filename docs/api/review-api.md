# 후기 API 명세

상태: 구현 완료 · 담당: 이현정 · 공통 규칙: [API 계약](api-spec.md#공통-규칙) · 자세한 동작: [후기 설계](../architecture/domains/review.md)

이 문서의 약속:
- 경로는 `/api`를 뺀 값입니다. 권한이 "로그인"인 API는 모두 401 `A003`이 있어 에러 표에서 뺍니다.
- 요청 검증 실패(형식·범위·필수 누락·모르는 enum 값)는 400 `C001`입니다. 에러 표에는 그 밖의 오류만 씁니다.
- 페이지는 1부터 시작하고 응답은 공통 `PageResponse`입니다. 시각 형식을 따로 적지 않은 칸은 ISO-8601입니다.

## 목차

- [후기 목록](#후기-목록)
- [후기 작성](#후기-작성)
- [후기 수정](#후기-수정)
- [후기 삭제](#후기-삭제)
- [후기 요약 실행](#후기-요약-실행)
- [후기 응답 DTO](#후기-응답-dto)

## 후기 목록
`GET /reviews` · 공개 (`mine=true`는 로그인)

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| recipeId (query) | long | N | 비우면 전체 레시피 |
| mine (query) | boolean | N | true면 내 후기만 |
| sort (query) | `LATEST`·`RATING` | N | 기본 `LATEST` |
| page (query) | int | N | 기본 1, 1 이상 |
| size (query) | int | N | 기본 10, 1~50 |

응답: `PageResponse<ReviewResponse>`

| HTTP | 코드 | 조건 |
|---|---|---|
| 401 | A003 | 로그인하지 않고 `mine=true` |

## 후기 작성
`POST /reviews` · 로그인

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| recipeId | long | Y | 레시피 ID |
| rating | int | Y | 1~5 |
| content | string | Y | 공백만은 안 됨, 100자 이하 |

응답: `ReviewResponse`

| HTTP | 코드 | 조건 |
|---|---|---|
| 403 | REVIEW_003 | 요리 완료 기록이 없는 레시피 |
| 404 | RECIPE_001 | 없는 레시피 |
| 409 | REVIEW_002 | 이 레시피에 이미 후기를 씀 |

## 후기 수정
`PATCH /reviews/{reviewId}` · 로그인

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| reviewId (path) | long | Y | 후기 ID |
| rating | int | Y | 1~5 |
| content | string | Y | 공백만은 안 됨, 100자 이하 |

응답: `ReviewResponse`

| HTTP | 코드 | 조건 |
|---|---|---|
| 404 | REVIEW_001 | 없는 후기이거나 남의 후기 |

## 후기 삭제
`DELETE /reviews/{reviewId}` · 로그인

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| reviewId (path) | long | Y | 후기 ID |

응답: 없음(`ApiResponse.ok()`)

| HTTP | 코드 | 조건 |
|---|---|---|
| 404 | REVIEW_001 | 없는 후기이거나 남의 후기 |

## 후기 요약 실행
`POST /admin/review-summaries/run` · ADMIN

요청: 없음

응답: `ReviewSummaryResult`

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| targetCount | int | N | 대상 레시피 수 |
| successCount | int | N | |
| skippedCount | int | N | 시간이 다 돼 다음으로 넘긴 수 포함 |
| failedCount | int | N | |
| elapsedMillis | long | N | |
| totalInputTokens | long | N | |
| totalOutputTokens | long | N | |
| totalCostUsd | number | N | |
| items | `ReviewSummaryItem[]` | N | 레시피마다 한 줄 |
| items[].recipeId | long | N | |
| items[].recipeName | string | Y | |
| items[].status | `SUCCESS`·`SKIPPED`·`FAILED` | N | |
| items[].reason | string | Y | 건너뜀·실패 이유 |
| items[].reviewCount | int | Y | 요약에 쓴 후기 수 |
| items[].inputTokens | int | Y | |
| items[].outputTokens | int | Y | |
| items[].costUsd | number | Y | |
| items[].previousSummary | string | Y | |
| items[].newSummary | string | Y | |

| HTTP | 코드 | 조건 |
|---|---|---|
| 401 | A003 | 로그인 안 함 |
| 403 | A004 | 관리자가 아님 |
| 409 | REVIEW_004 | 요약이 이미 도는 중 |
| 500 | COMMON_002 | 실행 중 예상하지 못한 오류 |

주의 사항
- 한 번에 20초까지만 돌고, 남은 레시피는 SKIPPED로 다음 실행에 넘깁니다.

## 후기 응답 DTO

`ReviewResponse`

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| id | long | N | 후기 ID |
| recipeId | long | N | |
| recipeName | string | Y | 레시피가 지워졌으면 null |
| rating | int | N | 1~5 |
| content | string | N | |
| nickname | string | Y | 작성자 닉네임 |
| mine | boolean | N | 로그인한 사람이 쓴 글인지(비로그인은 false) |
| createdAt | string(`yyyy-MM-dd HH:mm:ss`) | N | |
| updatedAt | string(`yyyy-MM-dd HH:mm:ss`) | N | |
