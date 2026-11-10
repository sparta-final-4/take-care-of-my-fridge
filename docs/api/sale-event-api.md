# 할인 행사 API 명세

상태: 구현 완료 · 담당: 장준혁 · 공통 규칙: [API 계약](api-spec.md#공통-규칙) · 자세한 동작: [할인 행사 설계](../architecture/domains/sale-event.md)

공통 응답·오류 코드·페이지·날짜 형식은 [공통 규칙](api-spec.md#공통-규칙)을 따릅니다.

## 클릭 수 기반 할인 상품 API

| 기능 | Method / Path | 성공 응답 |
|---|---|---|
| 클릭 수 기반 할인 상품 | GET /sales/popular | 200, `List<PopularSaleResponse>` |

- 메인 화면 할인 섹션이 씁니다. 로그인하지 않아도 부를 수 있고, 요청 값은 없습니다.
- `PopularSaleResponse`: productId, name, productType(`INGREDIENT`·`MEALKIT`), imageUrl, price(정가), sale(`SaleResponse`).
- **대상**: 진행 중인 특가·번개 드롭 상품입니다. 품절·판매 중지·번개 드롭 남은 수량 0인 상품은 뺍니다.
- **순서**: 오늘을 포함한 최근 7일 검색 결과 클릭이 많은 순입니다. 클릭이 같으면 할인율이 높은 순, 그것도 같으면 상품 ID 순입니다. 최대 6개입니다.

## 할인 행사 API

경로는 prefix(/api)를 뺀 값입니다. 모두 관리자 전용입니다(일반 회원 403, 로그인 안 하면 401).

| 기능 | Method / Path | 성공 응답 |
|---|---|---|
| 특가 등록 | POST /admin/sale-events/special | 201, `SaleEventsCreateResponse`(saleEventIds) |
| 번개 드롭 등록 | POST /admin/sale-events/flash-drop | 201, `SaleEventsCreateResponse`(saleEventIds) |
| 등록 후보 상품 조회 | GET /admin/sale-events/products | 200, `PageResponse<SaleCandidateProductResponse>` |
| 할인 행사 목록 | GET /admin/sale-events | 200, `PageResponse<SaleEventListResponse>` |
| 할인 행사 상세 | GET /admin/sale-events/{saleEventId} | 200, `SaleEventDetailResponse` |
| 할인 행사 수정 | PATCH /admin/sale-events/{saleEventId} | 200, 공통 성공 응답 |
| 할인 행사 종료 | PATCH /admin/sale-events/{saleEventId}/end | 200, 공통 성공 응답 |
| 할인 행사 삭제 | DELETE /admin/sale-events/{saleEventId} | 200, 공통 성공 응답 |

요청 값:

| 기능 | 요청 |
|---|---|
| 특가 등록 | Body: `startsAt`, `endsAt`, `items`: `[{ productId, discountRate(1~99) }]` |
| 번개 드롭 등록 | Body: 특가와 같고 항목마다 `limitedQuantity`(1 이상) 추가 |
| 등록 후보 상품 | Query: keyword(50자 이하), type(상품 종류), page, size(최대 100) |
| 목록 | Query: status, type, keyword, startFrom·startTo, endFrom·endTo, sort, direction(기본 DESC), page(기본 1), size(기본 10, 최대 100) |
| 수정 | Body: `startsAt`, `endsAt`, `items`: `[{ productId, discountRate, limitedQuantity? }]` |

주요 오류:

| 기능 | 오류 |
|---|---|
| 등록 | 400 시작 시각이 지금보다 과거·종료 시각이 시작 시각보다 늦지 않음·상품 중복·할인율 범위·한정 수량이 재고보다 많음, 404 상품 없음(`PRODUCT_001`), 409 이미 행사가 있는 상품(`SALE_001`)·판매 중지·품절 상품(`PRODUCT_005`) |
| 목록·후보 | 400 기간 순서·page·size 범위 |
| 상세 | 404 없는 행사(`SALE_002`) |
| 수정 | 400 입력값·상품 목록이 행사와 다름·번개 드롭 한정 수량 누락·특가에 한정 수량·한정 수량이 재고보다 많음, 404 `SALE_002`, 409 시작한 행사(`SALE_003`) |
| 종료 | 404 `SALE_002`, 409 진행 중이 아님(`SALE_004`) |
| 삭제 | 404 `SALE_002`, 409 시작 전이 아님(`SALE_005`) |

응답 필드:
- `SaleEventListResponse`(한 행): saleEventId, type, status, startsAt, endsAt, productId, productName, listPrice, salePrice, discountRate, limitedQuantity, remainingQuantity.
- `SaleEventDetailResponse`: saleEventId, type, status, startsAt, endsAt, items(productId, productName, listPrice, salePrice, discountRate, limitedQuantity, remainingQuantity).
- `SaleCandidateProductResponse`: productId, productType, name, imageUrl, price, stock.
- 종류 값: `SPECIAL`(특가), `FLASH_DROP`(번개 드롭). 상태 값: `SCHEDULED`(예정), `ACTIVE`(진행 중), `ENDED`(종료).

- **상품마다 행사 하나**: 등록하면 상품마다 같은 기간의 할인 행사가 하나씩 만들어집니다. 한 상품은 예정·진행 중인 할인 행사에 하나만 들 수 있습니다. 종료된 행사에 들었던 상품과, 종료 시각이 지났지만 상태 갱신이 아직 닫지 않은 행사의 상품은 다시 등록할 수 있습니다. 한 상품이라도 거절되면 모두 저장되지 않습니다.
- **할인가**: 정가와 할인율(1~99%)로 서버가 `정가 × (100 − 할인율) / 100`의 원 아래를 버려 계산합니다.
- **번개 드롭**: 한정 수량을 정해 등록하면 남은 수량이 한정 수량과 같게 시작합니다. 1인 1개입니다.
- **상태 변화**: 진행 여부는 시각(시작 ≤ 지금 < 종료)으로 판단합니다. 1분마다 도는 상태 갱신이 시작 시각이 지난 예정을 진행 중으로, 종료 시각이 지난 행사를 종료로 바꿉니다.
- **수정**: 시작 전 할인 행사만 고칠 수 있습니다. 행사에 든 상품 목록은 그대로 보내야 하고, 번개 드롭은 한정 수량이 필수이며 특가는 한정 수량을 보내지 않습니다. 정가는 상품의 지금 정가로 다시 읽으며 번개 드롭 남은 수량은 한정 수량과 같게 다시 맞춥니다. 하나라도 거절되면 아무것도 바뀌지 않습니다.
- **종료**: 시각상 진행 중인 할인 행사만 종료합니다(상태 갱신이 늦어 아직 예정으로 남아 있어도 시작 시각이 지났으면 종료할 수 있습니다). 상태만 종료로 바꾸고 종료 시각과 행사 항목은 그대로이며, 종료한 행사는 다시 열리지 않습니다.
- **삭제**: 시작 전 할인 행사만 지웁니다. 행사와 행사 항목이 함께 지워집니다.
- **동시 요청**: 수정·종료·삭제는 행사 행을 잠근 뒤 상태를 확인하므로 상태 갱신과 겹쳐도 한쪽이 끝난 뒤 처리됩니다.
- **정가 표시**: 목록·상세의 정가는 예정·진행 중이면 상품의 현재 정가, 종료되면 행사 항목에 저장된 등록 당시 정가입니다.
- **목록**: 상품마다 한 행으로 나옵니다. 시작·종료 날짜는 하나만 주면 그 하루로 봅니다. 정렬 기준은 상품 이름·종류·상태·정가·할인가·할인율·한정 수량·남은 수량·시작·종료이고, 정렬 기준을 주지 않으면 최신 행사 순이고, 기준이 같으면 최신 행사가 먼저입니다.
- **등록 후보 상품**: 판매 중이고 재고가 있으며 예정·진행 중인 할인 행사에 들지 않은 상품만 나옵니다.
