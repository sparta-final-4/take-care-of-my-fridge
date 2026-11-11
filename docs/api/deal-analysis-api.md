# 할인 후보 API 명세

상태: 구현 완료 · 담당: cookiez · 공통 규칙: [API 계약](api-spec.md#공통-규칙) · 자세한 동작: [할인 후보 설계](../architecture/domains/deal-analysis.md)

공통 응답·오류 코드·페이지·날짜 형식은 [공통 규칙](api-spec.md#공통-규칙)을 따릅니다. 이 문서의 경로는 `/api`를 뺀 값이고, 오류 표에는 공통 오류(401 `A003`, 403 `A004`, 400 `C001`)를 빼고 이 API만의 오류를 적습니다. 모든 API는 ADMIN 전용입니다. 이 API들은 상품 가격을 바꾸지 않습니다.

## 목차

- [후보 분석 실행](#후보-분석-실행)
- [분석 조회](#분석-조회)
- [최신 분석 조회](#최신-분석-조회)
- [시연 분석 조회](#시연-분석-조회)
- [검토 결과 저장](#검토-결과-저장)
- [할인 행사 등록](#할인-행사-등록)
- [할인 후보 응답 DTO](#할인-후보-응답-dto)
- [오류 코드](#오류-코드)

## 후보 분석 실행
`POST /admin/deal-analyses` · ADMIN

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| targetWeekStart | date | Y | 대상 주 월요일 |
| dayOfWeek | `MON`~`SUN` | Y | 대상 요일. 대상일(월요일 + 요일)은 오늘이거나 그 뒤 |
| discountRate | int | Y | 할인율 1~99 |

응답: `201` `DealAnalysisResponse` ([할인 후보 응답 DTO](#할인-후보-응답-dto))

| HTTP | 코드 | 조건 |
|---|---|---|
| 422 | D007 | 실제 일반 특가 이력이 부족함. 메시지에 부족한 종료 특가 건수와 할인율 종류를 담음 |
| 500 | D008 | 학습·예측 자료 값이 올바르지 않음 |

주의 사항
- 실행할 때마다 분석 1건을 새로 저장하고 작업 이력에 `DEAL_ANALYSIS`로 남깁니다.

## 분석 조회
`GET /admin/deal-analyses/{analysisId}` · ADMIN

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| analysisId (path) | long | Y | 분석 ID |

응답: `DealAnalysisResponse`

| HTTP | 코드 | 조건 |
|---|---|---|
| 404 | D002 | 분석 없음 |

## 최신 분석 조회
`GET /admin/deal-analyses/latest` · ADMIN

요청: 없음

응답: `DealAnalysisResponse` (내가 마지막으로 실행한 분석)

| HTTP | 코드 | 조건 |
|---|---|---|
| 404 | D002 | 내가 실행한 분석이 없음. `error.message`에 분석 가능한 자료 현황을 담음 |

## 시연 분석 조회
`GET /admin/deal-analyses/demo` · ADMIN

요청: 없음

응답: `DealAnalysisResponse` (`syntheticData=true`인 저장 결과)

| HTTP | 코드 | 조건 |
|---|---|---|
| 404 | D002 | 시연 결과가 없음 |

## 검토 결과 저장
`PUT /admin/deal-analyses/{analysisId}/reviews` · ADMIN

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| analysisId (path) | long | Y | 분석 ID |
| reviews | `Review[]` | Y | 1개 이상 |
| reviews[].candidateId | long | Y | 후보 ID, 1 이상 |
| reviews[].reviewStatus | `SELECTED`·`HELD` | Y | `UNREVIEWED`로는 저장할 수 없음 |

응답: `DealReviewResponse`

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| lastSavedAt | datetime | N | 저장 시각 |
| reviewCounts | `ReviewCounts` | N | 저장 뒤 검토 수 |

| HTTP | 코드 | 조건 |
|---|---|---|
| 400 | D003 | 이 분석에 없는 후보 또는 제외 상품 |
| 404 | D002 | 분석 없음 |
| 409 | D004 | 이미 행사 요청한 후보의 검토 상태를 바꿈 |

주의 사항
- 보낸 후보만 바꿉니다. 미검토가 남아 있어도 저장할 수 있습니다.

## 할인 행사 등록
`POST /admin/deal-analyses/{analysisId}/event-requests` · ADMIN

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| analysisId (path) | long | Y | 분석 ID |
| candidateId | long | Y | `SELECTED`이고 아직 행사에 등록되지 않은 후보 |
| type | `SPECIAL`·`FLASH_DROP` | Y | 특가·번개 드롭 |
| startsAt | datetime | Y | 시작 시각 |
| endsAt | datetime | Y | 종료 시각. `startsAt`보다 늦어야 함 |
| limitedQuantity | int | N | 번개 드롭이면 필수, 1 이상 |

응답: `201` `DealEventResponse`

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| candidateId | long | N | 후보 ID |
| saleEventId | long | N | 만든 행사 ID |
| eventRequestStatus | `APPROVED` | N | 등록 결과 |

| HTTP | 코드 | 조건 |
|---|---|---|
| 400 | D003 | 이 분석의 후보가 아님 |
| 404 | D002 | 분석 없음 |
| 409 | D005 | 선택되지 않았거나 이미 행사에 등록된 후보 |
| 409 | D006 | 가상 데이터 분석의 후보 (dev 환경 제외) |

주의 사항
- 할인율은 분석의 `discountRate`를 씁니다. 행사 생성 쪽 오류(예: 상품에 이미 행사가 있음)는 [할인 행사 API](sale-event-api.md)의 코드로 옵니다.
- 행사 생성에 실패하면 후보 상태는 바뀌지 않습니다. 성공하면 작업 이력에 `EVENT_REQUEST`로 남깁니다.

## 할인 후보 응답 DTO

`DealAnalysisResponse`

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| analysisId | long | N | 분석 ID |
| targetWeekStart | date | N | 대상 주 월요일 |
| dayOfWeek | `MON`~`SUN` | N | 대상 요일 |
| discountRate | int | N | 할인율 |
| minStock | int | N | 후보로 볼 최소 재고 (서버 기준값) |
| modelVersion | string | N | 할인 효과 모델 버전 |
| dataCutoffAt | datetime | N | 자료 기준 시각 |
| createdAt | datetime | N | 실행 시각 |
| lastSavedAt | datetime | Y | 마지막 검토 저장 시각 |
| syntheticData | boolean | N | 가상 데이터 결과면 true |
| reviewCounts | `ReviewCounts` | N | 검토 수 |
| candidates | `Candidate[]` | N | 후보 상품. 검색 클릭 − 구매가 큰 순 |
| excluded | `Excluded[]` | N | 제외 상품. 후보 ID 순 |

`ReviewCounts`: selected(int)·held(int)·unreviewed(int)

`Candidate`

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| candidateId | long | N | 후보 ID |
| productId | long | N | 상품 ID |
| productName | string | N | 상품명 |
| regularPrice | int | N | 정상가 |
| discountPrice | int | N | 할인가 |
| expectedQtyRegular | int | Y | 정상가일 때 예상 판매량 |
| expectedQtyDiscount | int | Y | 할인할 때 예상 판매량 |
| expectedIncrease | int | Y | 예상 증가량 |
| searchClicks | int | N | 최근 4주 같은 요일 검색 클릭 합계 |
| purchases | int | N | 최근 4주 같은 요일 구매 합계 |
| currentStock | int | N | 현재 재고 |
| candidateReason | string | Y | 후보로 고른 이유 |
| reviewStatus | `UNREVIEWED`·`SELECTED`·`HELD` | N | 검토 상태 |
| eventRequestStatus | `NONE`·`REQUESTED`·`APPROVED`·`REJECTED` | N | 행사 등록 상태 |

`Excluded`

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| candidateId | long | N | 후보 ID |
| productId | long | N | 상품 ID |
| productName | string | N | 상품명 |
| excludeReason | `TOP_SELLER_ON_DAY`·`LOW_STOCK` | N | 그 요일 판매 상위·최소 재고 미만 |
| avgSalesOnDay | int | N | 그 요일 평균 판매량 |
| currentStock | int | N | 현재 재고 |

## 오류 코드

| 코드 | HTTP | 이름 | 메시지 |
|---|---|---|---|
| D002 | 404 | DEAL_ANALYSIS_NOT_FOUND | 할인 후보 분석 결과가 없습니다. |
| D003 | 400 | DEAL_CANDIDATE_NOT_IN_ANALYSIS | 이 분석의 할인 후보가 아닙니다. |
| D004 | 409 | DEAL_REVIEW_LOCKED | 이미 행사 요청한 후보는 검토 상태를 바꿀 수 없습니다. |
| D005 | 409 | DEAL_EVENT_NOT_AVAILABLE | 선택되지 않았거나 이미 행사에 등록된 후보입니다. |
| D006 | 409 | DEAL_SYNTHETIC_ANALYSIS | 가상 데이터 분석 결과로는 행사를 등록할 수 없습니다. |
| D007 | 422 | DEAL_TRAINING_DATA_INSUFFICIENT | 실제 일반 특가 이력이 부족해 할인 효과를 학습할 수 없습니다. |
| D008 | 500 | DEAL_MODEL_INPUT_INVALID | 할인 효과 계산 자료가 올바르지 않습니다. |
