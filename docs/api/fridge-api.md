# 냉장고 API 명세

상태: 구현 완료 · 담당: 이현정 · 공통 규칙: [API 계약](api-spec.md#공통-규칙) · 자세한 동작: [냉장고 설계](../architecture/domains/fridge.md)

공통 응답·오류 코드·페이지·날짜 형식은 [공통 규칙](api-spec.md#공통-규칙)을 따릅니다. 이 문서의 경로는 `/api`를 뺀 값이고, 에러 표에는 공통 오류(401 `A003`, 400 `C001`)를 빼고 이 API만의 오류를 적습니다.

## 목차

- [재료 목록](#재료-목록)
- [재료 이력](#재료-이력)
- [재료 등록](#재료-등록)
- [재료 수정](#재료-수정)
- [재료 빼기](#재료-빼기)
- [재료 비우기](#재료-비우기)
- [냉장고 응답 DTO](#냉장고-응답-dto)

## 재료 목록
`GET /fridge/items` · 로그인

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| sort (query) | `EXPIRATION`·`CREATED`·`NAME` | N | 기본 `EXPIRATION`(소비기한 순). `CREATED`는 최신 등록순 |

응답: `List<FridgeItemResponse>` (회원 재료 전체. 탭 나누기는 프론트)

주의 사항
- 기본 양념 탭과 개수 제외는 `basicSeasoning`으로 프론트가 나눕니다.

## 재료 이력
`GET /fridge/logs` · 로그인

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| action (query) | `CONSUMED`·`DISCARDED` | N | 비우면 둘 다 |
| from (query) | date | N | 이 날짜부터(`created_at` 기준) |
| to (query) | date | N | 이 날짜 하루 끝까지 |
| page (query) | int | N | 기본 1, 1 이상 |
| size (query) | int | N | 기본 20, 1~100 |

응답: `PageResponse<FridgeItemLogResponse>` (최신순)

| HTTP | 코드 | 조건 |
|---|---|---|
| 400 | C001 | `from`이 `to`보다 늦음 |

## 재료 등록
`POST /fridge/items` · 로그인

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| items | `FridgeItemCreateItem[]` | Y | 1~20개 |
| items[].rawName | string | Y | 재료 이름, 200자 이하 |
| items[].quantity | number | Y | 0보다 큼 |
| items[].storage | `REFRIGERATED`·`FROZEN`·`ROOM_TEMP` | N | 비우면 이은 재료의 기본 보관방식 |
| items[].expirationDate | date | N | 비우면 오늘 + 재료 기본 보관 일수 |

응답: `List<FridgeItemResponse>`

주의 사항
- 이름은 표준 재료에 자동으로 잇고, 사전에 없으면 직접 입력 재료로 잇습니다.

## 재료 수정
`PATCH /fridge/items/{fridgeId}` · 로그인

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| fridgeId (path) | long | Y | 냉장고 재료 ID |
| rawName | string | N | 공백만은 안 됨, 200자 이하 |
| quantity | number | N | 0보다 큼 |
| storage | `REFRIGERATED`·`FROZEN`·`ROOM_TEMP` | N | |
| expirationDate | date | N | |

응답: `FridgeItemResponse`

| HTTP | 코드 | 조건 |
|---|---|---|
| 404 | FRIDGE_001 | 없는 재료이거나 남의 재료 |

주의 사항
- 보낸 칸만 바꾸고 null인 칸은 그대로 둡니다.
- `rawName`이 바뀌었을 때만 표준 재료를 다시 잇습니다(밀키트는 다시 잇지 않음).

## 재료 빼기
`POST /fridge/items/{fridgeId}/take-out` · 로그인

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| fridgeId (path) | long | Y | 냉장고 재료 ID |
| action | `CONSUMED`·`DISCARDED` | Y | 먹었어요·버렸어요 |
| quantity | number | N | 0보다 큼. null이거나 남은 양 이상이면 전부 |

응답: `FridgeItemTakeOutResponse`

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| fridgeId | long | N | 냉장고 재료 ID |
| removed | boolean | N | 남은 양이 없어 지워졌는지 |
| remainingQuantity | number | N | 남은 양(지워졌으면 0) |

| HTTP | 코드 | 조건 |
|---|---|---|
| 400 | FRIDGE_005 | 밀키트를 정수가 아닌 개수로 뺌 |
| 404 | FRIDGE_001 | 없는 재료이거나 남의 재료 |

주의 사항
- 남은 양이 0.05 미만이 되면 0으로 보고 줄을 지웁니다.

## 재료 비우기
`DELETE /fridge/items/{fridgeId}` · 로그인

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| fridgeId (path) | long | Y | 냉장고 재료 ID |
| reason (query) | `CONSUMED`·`DISCARDED`·`WRONG_ENTRY` | Y | `WRONG_ENTRY`는 이력 없이 지움 |

응답: 없음(`ApiResponse.ok()`)

| HTTP | 코드 | 조건 |
|---|---|---|
| 404 | FRIDGE_001 | 없는 재료이거나 남의 재료 |

## 냉장고 응답 DTO

`FridgeItemResponse`

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| id | long | N | 냉장고 재료 ID |
| ingredientId | long | Y | 이은 표준 재료 ID |
| ingredientName | string | Y | 표준 재료 이름 |
| baseUnit | string | Y | 기본 단위 |
| rawName | string | N | 등록 이름 |
| quantity | number | N | 남은 양 |
| storage | `REFRIGERATED`·`FROZEN`·`ROOM_TEMP` | N | 보관방식 |
| expirationDate | date | N | 소비기한 |
| source | `MANUAL`·`COMMERCE` | N | 직접 등록·구매 확정 입고 |
| orderItemId | long | Y | 구매 확정 입고만 있음 |
| imminent | boolean | N | 사용 중단 예정, `status`를 씀 |
| expired | boolean | N | 사용 중단 예정, `status`를 씀 |
| dDay | long | N | 소비기한까지 남은 일수(오늘 0, 지나면 음수) |
| status | `NORMAL`·`IMMINENT`·`EXPIRED` | N | 임박은 D-3 이내 |
| ingredientType | `NORMAL`·`CUSTOM`·`MEALKIT` | N | 일반·직접 입력·밀키트 |
| basicSeasoning | boolean | N | 기본 양념 |
| createdAt | date-time | N | |
| updatedAt | date-time | N | |
| productId | long | Y | 구매 확정 줄의 상품 ID. 직접 등록·예전 줄은 null |

`FridgeItemLogResponse`

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| id | long | N | 이력 ID |
| ingredientId | long | Y | 표준 재료 ID |
| ingredientName | string | Y | 표준 재료 이름 |
| baseUnit | string | Y | 기본 단위 |
| rawName | string | N | 등록 이름 |
| quantity | number | N | 실제로 뺀 양 |
| action | `CONSUMED`·`DISCARDED` | N | |
| cookedByRecipe | boolean | N | 요리 완료로 쓴 기록인지 |
| createdAt | date-time | N | |
