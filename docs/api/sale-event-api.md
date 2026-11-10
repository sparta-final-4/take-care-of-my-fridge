# 할인 행사 API 명세

상태: 구현 완료 · 담당: 장준혁 · 공통 규칙: [API 계약](api-spec.md#공통-규칙) · 자세한 동작: [할인 행사 설계](../architecture/domains/sale-event.md)

공통 응답·오류 코드·페이지·날짜 형식은 [공통 규칙](api-spec.md#공통-규칙)을 따릅니다. 이 문서의 경로는 `/api`를 뺀 값이고, 에러 표에는 공통 오류(401 `A003`, 403 `A004`, 400 `C001`)를 빼고 이 API만의 오류를 적었습니다. 관리자 API는 관리자만 부를 수 있습니다(일반 회원 403 `A004`, 로그인하지 않으면 401 `A003`).

## 목차

- [클릭 수 기반 할인 상품](#클릭-수-기반-할인-상품)
- [특가 등록](#특가-등록)
- [번개 드롭 등록](#번개-드롭-등록)
- [등록 후보 상품 조회](#등록-후보-상품-조회)
- [할인 행사 목록](#할인-행사-목록)
- [할인 행사 상세](#할인-행사-상세)
- [할인 행사 수정](#할인-행사-수정)
- [할인 행사 종료](#할인-행사-종료)
- [할인 행사 삭제](#할인-행사-삭제)
- [할인 행사 응답 DTO](#할인-행사-응답-dto)

## 클릭 수 기반 할인 상품
`GET /sales/popular` · 로그인 불필요

요청: 없음

응답: `List<PopularSaleResponse>`

주의 사항
- 메인 화면 할인 섹션이 씁니다.
- **대상**: 진행 중인 특가·번개 드롭 상품입니다. 품절·판매 중지·번개 드롭 남은 수량 0인 상품은 뺍니다.
- **순서**: 오늘을 포함한 최근 7일 검색 결과 클릭이 많은 순입니다. 클릭이 같으면 할인율이 높은 순, 그것도 같으면 상품 ID 순입니다. 최대 6개입니다.

## 특가 등록
`POST /admin/sale-events/special` · 관리자

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| startsAt | datetime | Y | 시작 시각. 지금 이후 |
| endsAt | datetime | Y | 종료 시각. 시작 시각보다 늦어야 함 |
| items | `SpecialSaleItemRequest[]` | Y | 1개 이상. 같은 상품을 두 번 넣을 수 없음 |
| items[].productId | long | Y | 상품 ID |
| items[].discountRate | int | Y | 할인율. 1~99 |

응답: `SaleEventsCreateResponse`. 201입니다.

| HTTP | 코드 | 조건 |
|---|---|---|
| 400 | C001 | 시작 시각이 지금보다 과거, 종료 시각이 시작 시각보다 늦지 않음, 상품 중복, 할인율 범위 |
| 404 | PRODUCT_001 | 없는 상품 |
| 409 | SALE_001 | 이미 예정·진행 중인 행사가 있는 상품 |
| 409 | PRODUCT_005 | 판매 중지·품절 상품 |

주의 사항
- **상품마다 행사 하나**: 등록하면 상품마다 같은 기간의 할인 행사가 하나씩 만들어집니다. 한 상품은 예정·진행 중인 할인 행사에 하나만 들 수 있습니다. 종료된 행사에 들었던 상품과, 종료 시각이 지났지만 상태 갱신이 아직 닫지 않은 행사의 상품은 다시 등록할 수 있습니다. 한 상품이라도 거절되면 모두 저장되지 않습니다.
- **할인가**: 정가와 할인율(1~99%)로 서버가 `정가 × (100 − 할인율) / 100`의 원 아래를 버려 계산합니다.

## 번개 드롭 등록
`POST /admin/sale-events/flash-drop` · 관리자

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| startsAt | datetime | Y | 시작 시각. 지금 이후 |
| endsAt | datetime | Y | 종료 시각. 시작 시각보다 늦어야 함 |
| items | `FlashDropItemRequest[]` | Y | 1개 이상. 같은 상품을 두 번 넣을 수 없음 |
| items[].productId | long | Y | 상품 ID |
| items[].discountRate | int | Y | 할인율. 1~99 |
| items[].limitedQuantity | int | Y | 한정 수량. 1 이상 |

응답: `SaleEventsCreateResponse`. 201입니다.

| HTTP | 코드 | 조건 |
|---|---|---|
| 400 | C001 | 특가 등록의 400 조건, 한정 수량이 1 미만이거나 재고보다 많음 |
| 404 | PRODUCT_001 | 없는 상품 |
| 409 | SALE_001 | 이미 예정·진행 중인 행사가 있는 상품 |
| 409 | PRODUCT_005 | 판매 중지·품절 상품 |

주의 사항
- 등록하면 남은 수량이 한정 수량과 같게 시작합니다. 1인 1개이며 구매 한도는 요청으로 받지 않습니다.
- 상품마다 행사가 하나씩 만들어지는 규칙과 할인가 계산은 특가 등록과 같습니다.
- 등록할 때는 알림을 보내지 않습니다. 시작 시각이 되어 상태 갱신이 행사를 열면 그때 알림을 보냅니다.

## 등록 후보 상품 조회
`GET /admin/sale-events/products` · 관리자

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| keyword (query) | string | N | 상품 이름 검색. 50자 이하 |
| type (query) | `ProductType` | N | 상품 종류 |
| page (query) | int | N | 기본 1, 1 이상 |
| size (query) | int | N | 기본 10, 1~100 |

응답: `PageResponse<SaleCandidateProductResponse>`

| HTTP | 코드 | 조건 |
|---|---|---|
| 400 | C001 | keyword가 50자를 넘음, page·size 범위 |

주의 사항
- 판매 중이고 재고가 있으며 예정·진행 중인 할인 행사에 들지 않은 상품만 나옵니다.

## 할인 행사 목록
`GET /admin/sale-events` · 관리자

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| status (query) | `SaleEventStatus` | N | 행사 상태 |
| type (query) | `SaleEventType` | N | 행사 종류 |
| keyword (query) | string | N | 상품 이름 검색. 50자 이하 |
| startFrom, startTo (query) | date | N | 시작 날짜 조건. 하나만 주면 그 하루 |
| endFrom, endTo (query) | date | N | 종료 날짜 조건. 하나만 주면 그 하루 |
| sort (query) | `SaleEventListSort` | N | 정렬 기준. 비우면 최신 행사순 |
| direction (query) | `ASC`·`DESC` | N | 기본 `DESC` |
| page (query) | int | N | 기본 1, 1 이상 |
| size (query) | int | N | 기본 10, 1~100 |

응답: `PageResponse<SaleEventListResponse>`

| HTTP | 코드 | 조건 |
|---|---|---|
| 400 | C001 | 끝 날짜가 시작 날짜보다 빠름, keyword·page·size 범위 |

주의 사항
- **한 행**: 행사에 든 상품 하나가 한 행입니다.
- **날짜 조건**: 둘 다 주면 그 기간(양 끝 포함)을 찾습니다.
- **정렬**: 정렬 기준은 `SaleEventListSort`의 값입니다. 기준이 같으면 최신 행사가 먼저입니다.
- **정가 표시**: 정가는 예정·진행 중이면 상품의 현재 정가, 종료되면 행사 항목에 저장된 등록 당시 정가입니다.

## 할인 행사 상세
`GET /admin/sale-events/{saleEventId}` · 관리자

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| saleEventId (path) | long | Y | 할인 행사 ID |

응답: `SaleEventDetailResponse`

| HTTP | 코드 | 조건 |
|---|---|---|
| 404 | SALE_002 | 없는 행사 |

## 할인 행사 수정
`PATCH /admin/sale-events/{saleEventId}` · 관리자

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| saleEventId (path) | long | Y | 할인 행사 ID |
| startsAt | datetime | Y | 시작 시각. 지금 이후 |
| endsAt | datetime | Y | 종료 시각. 시작 시각보다 늦어야 함 |
| items | `SaleEventItemUpdateRequest[]` | Y | 행사에 든 상품을 빠짐없이 한 번씩 |
| items[].productId | long | Y | 상품 ID |
| items[].discountRate | int | Y | 할인율. 1~99 |
| items[].limitedQuantity | int | 번개 드롭만 | 한정 수량. 1 이상. 특가는 보내지 않음 |

응답: 없음(`ApiResponse.ok()`)

| HTTP | 코드 | 조건 |
|---|---|---|
| 400 | C001 | 입력값, 시작 시각이 지금보다 과거, 상품 목록이 행사와 다름, 번개 드롭 한정 수량 누락, 특가에 한정 수량, 한정 수량이 재고보다 많음 |
| 404 | SALE_002 | 없는 행사 |
| 409 | SALE_003 | 이미 시작한 행사 |

주의 사항
- 시작 전 할인 행사만 고칠 수 있습니다. 상품을 넣거나 빼지는 않습니다.
- 정가는 상품의 지금 정가로 다시 읽으며 번개 드롭 남은 수량은 한정 수량과 같게 다시 맞춥니다. 하나라도 거절되면 아무것도 바뀌지 않습니다.
- 수정은 행사 행을 잠근 뒤 상태를 확인하므로 상태 갱신과 겹쳐도 한쪽이 끝난 뒤 처리됩니다.

## 할인 행사 종료
`PATCH /admin/sale-events/{saleEventId}/end` · 관리자

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| saleEventId (path) | long | Y | 할인 행사 ID |

응답: 없음(`ApiResponse.ok()`)

| HTTP | 코드 | 조건 |
|---|---|---|
| 404 | SALE_002 | 없는 행사 |
| 409 | SALE_004 | 진행 중이 아님 |

주의 사항
- 시각상 진행 중인 할인 행사만 종료합니다. 상태 갱신이 늦어 아직 예정으로 남아 있어도 시작 시각이 지났으면 종료할 수 있습니다.
- 상태만 종료로 바꾸고 종료 시각과 행사 항목은 그대로이며, 종료한 행사는 다시 열리지 않습니다.
- 종료는 행사 행을 잠근 뒤 상태를 확인하므로 상태 갱신과 겹쳐도 한쪽이 끝난 뒤 처리됩니다.

## 할인 행사 삭제
`DELETE /admin/sale-events/{saleEventId}` · 관리자

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| saleEventId (path) | long | Y | 할인 행사 ID |

응답: 없음(`ApiResponse.ok()`)

| HTTP | 코드 | 조건 |
|---|---|---|
| 404 | SALE_002 | 없는 행사 |
| 409 | SALE_005 | 시작 전이 아님 |

주의 사항
- 시작 전 할인 행사만 지웁니다. 행사와 행사 항목이 함께 지워집니다.
- 삭제는 행사 행을 잠근 뒤 상태를 확인하므로 상태 갱신과 겹쳐도 한쪽이 끝난 뒤 처리됩니다.

## 할인 행사 응답 DTO

종류 값은 `SPECIAL`(특가), `FLASH_DROP`(번개 드롭)이고, 상태 값은 `SCHEDULED`(예정), `ACTIVE`(진행 중), `ENDED`(종료)입니다. 진행 여부는 시각(시작 ≤ 지금 < 종료)으로 판단하고, 1분마다 도는 상태 갱신이 상태 값을 맞춥니다. 시각은 한국 시간입니다.

`SaleEventsCreateResponse` (특가·번개 드롭 등록)

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| saleEventIds | `long[]` | N | 만든 행사 ID. 고른 상품 순서 |

`PopularSaleResponse` (클릭 수 기반 할인 상품)

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| productId | long | N | 상품 ID |
| name | string | N | 상품 이름 |
| productType | `INGREDIENT`·`MEALKIT` | N | 상품 종류 |
| imageUrl | string | Y | 상품 이미지 |
| price | int | N | 정가 |
| sale | `SaleResponse` | N | 진행 중 할인 |

`SaleResponse`는 상품 목록·상세와 같은 모양이며 [상품 할인·품절 필드](api-spec.md#상품-할인품절-필드)를 따릅니다.

`SaleCandidateProductResponse` (등록 후보 상품)

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| productId | long | N | 상품 ID |
| productType | `INGREDIENT`·`MEALKIT` | N | 상품 종류 |
| name | string | N | 상품 이름 |
| imageUrl | string | Y | 상품 이미지 |
| price | int | N | 정가 |
| stock | int | N | 재고 |

`SaleEventListResponse` (목록의 한 행)

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| saleEventId | long | N | 행사 ID |
| type | `SaleEventType` | N | 행사 종류 |
| status | `SaleEventStatus` | N | 행사 상태 |
| startsAt, endsAt | datetime | N | 시작·종료 시각 |
| productId | long | N | 상품 ID |
| productName | string | N | 상품 이름 |
| listPrice | int | N | 정가 |
| salePrice | int | N | 할인가 |
| discountRate | int | N | 할인율 |
| limitedQuantity | int | Y | 한정 수량. 번개 드롭만 값이 있음 |
| remainingQuantity | int | Y | 남은 수량. 번개 드롭만 값이 있음 |

`SaleEventListSort` 값은 `PRODUCT_NAME`, `TYPE`, `STATUS`, `LIST_PRICE`, `SALE_PRICE`, `DISCOUNT_RATE`, `LIMITED_QUANTITY`, `REMAINING_QUANTITY`, `STARTS_AT`, `ENDS_AT`입니다.

`SaleEventDetailResponse` (상세)

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| saleEventId | long | N | 행사 ID |
| type | `SaleEventType` | N | 행사 종류 |
| status | `SaleEventStatus` | N | 행사 상태 |
| startsAt, endsAt | datetime | N | 시작·종료 시각 |
| items | `SaleEventDetailItem[]` | N | 행사에 든 상품 |

`SaleEventDetailItem`

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| productId | long | N | 상품 ID |
| productName | string | N | 상품 이름 |
| listPrice | int | N | 정가. 종료된 행사는 등록 당시 정가, 그 밖에는 상품의 현재 정가 |
| salePrice | int | N | 할인가 |
| discountRate | int | N | 할인율 |
| limitedQuantity | int | Y | 한정 수량. 번개 드롭만 값이 있음 |
| remainingQuantity | int | Y | 남은 수량. 번개 드롭만 값이 있음 |
