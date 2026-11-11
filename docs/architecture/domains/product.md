# 상품 설계

상태: 구현 완료 · 담당: 강준모 · API: [상품 API 명세](../../api/product-api.md)

## 목적과 책임
- 해결할 사용자 문제: 부족한 재료나 밀키트를 골라 산다. 관리자는 판매 상품을 등록·수정·판매 중지한다.
- 이 도메인이 소유하는 데이터: 상품(`products`), 재료 상품 상세(`ingredient_products`), 밀키트 상품 상세(`mealkit_products`).
- 다른 도메인에 맡기는 책임: 할인가·품절 판단(할인 행사, `SaleEventReader`), 재고 차감·복구 시점(주문·환불), 검색어·관련도 검색(통합검색), 이미지 파일 저장(S3).
- 냉장고 재료와 판매 상품은 다른 개체입니다. 재료 상품은 `ingredient_products.ingredient_id`로 표준 재료에 잇기만 합니다.

## 동작 규칙
| 기능 | 규칙 | 근거 |
|---|---|---|
| 공개 범위 | 공개 목록·상세는 `ON_SALE`·`SOLD_OUT`만 보여 주고, 판매 중지(`STOPPED`) 상품의 상세는 없는 상품과 같이 404 `PRODUCT_001`입니다. 관리자 조회는 세 상태를 모두 봅니다 | `ProductService.PUBLIC_STATUSES` |
| 목록 조회 | MySQL로만 조회합니다. `keyword`는 상품명에 들어 있는지(대소문자 무시), 가격 범위는 정가 기준으로 양 끝을 포함합니다. `RELEVANCE`는 `LATEST`(ID 내림차순)와 같습니다 | `ProductRepositoryImpl.findProductsByCondition` |
| 판매가 붙이기 | 상품은 상품 서비스가, 할인·품절은 할인 행사 도메인이 정하고 퍼사드가 합칩니다. 등록·수정 응답의 판매가는 저장이 커밋된 뒤 읽습니다 | `ProductFacade` |
| 상품 종류별 상세 | 재료 상품은 `ingredient`, 밀키트 상품은 `mealkit` 하나만 받습니다. 종류는 상세 테이블이 달라 바꿀 수 없고, 다른 상세를 보내면 400 `C001`입니다. 상세 행이 없는 상품에 상세를 보내면 500 `PRODUCT_007`입니다 | `ProductCreateRequest.isDetailMatchingType`, `ProductService.updateProduct` |
| 연결 재료 확인 | `ingredient_products.ingredient_id`는 FK 없이 ID로만 참조하므로 저장 전에 확인합니다. 없으면 404 `INGREDIENT_002`, 직접 입력용(`ING_CUSTOM`)·밀키트용(`ING_MEALKIT`) 기본 재료면 400 `PRODUCT_006`입니다 | `ProductService.validateLinkableIngredient` |
| 가격·재고 상한 | 정가는 1원 이상 1,000만 원 이하, 재고는 10만 이하입니다. 주문 금액(가격 × 수량)이 int를 넘지 않게 상한을 둡니다 | `ProductCreateRequest` |
| 상태 | 등록하면 `ON_SALE`입니다. 수정 때 판매 중지가 아니면 상태는 재고로 정합니다(재고 0 = `SOLD_OUT`). 요청한 상태가 재고와 어긋나도 재고를 따릅니다. 주문으로 재고가 0이 되면 품절, 취소·환불로 늘면 판매 중으로 바뀝니다 | `Product.changeSales`, `Product.decreaseStock`, `Product.increaseStock` |
| 수정 동시성 | 수정은 상품 행을 `SELECT ... FOR UPDATE`로 잠그고 바꿉니다 | `ProductRepository.findByIdForUpdate` |
| 삭제 | 주문·장바구니가 `productId`로 참조하므로 행을 지우지 않고 `STOPPED`로 바꿉니다. 이미 판매 중지여도 성공입니다. 원자적 UPDATE가 주문 생성과 같은 행에 쓰기 잠금을 걸어, 삭제 직후 진행 중인 주문이 판매 중지 상품을 사지 않게 합니다 | `ProductService.deleteProduct` |
| 주문 가격 보존 | 주문 항목은 상품명·사진·정가·판매가를 따로 저장하므로, 상품 수정·삭제가 기존 주문 금액을 바꾸지 않습니다 | `OrderItem` |
| 검색 색인 | 등록·수정·삭제 뒤 `ProductChangedEvent`를 내고, 통합검색이 그 상품을 ES에 다시 넣습니다 | `ProductService`, `ProductIndexEventListener` |
| 이미지 업로드 | 브라우저가 S3에 바로 올리도록 5분짜리 Presigned PUT URL을 만듭니다. Content-Type·크기를 서명에 넣어 다른 파일은 S3가 거부합니다. 키는 매번 새 UUID(`images/products/{UUID}.{확장자}`)라 CloudFront 캐시를 지울 필요가 없습니다. 업로드를 끈 환경은 503 `IMAGE_001`입니다 | `ImageUploadUrlService` |
