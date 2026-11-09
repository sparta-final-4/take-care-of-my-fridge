# 냉장고 설계

상태: 구현 완료 · 담당: 이현정 · API: [냉장고 API 명세](../../api/fridge-api.md)

## 목적과 책임
- 해결할 사용자 문제: 내 냉장고에 무엇이 얼마나 있고 언제까지 먹어야 하는지 본다.
- 이 도메인이 소유하는 데이터: 냉장고 재료(`fridge`), 먹었어요·버렸어요 이력(`fridge_item_logs`).
- 다른 도메인에 맡기는 책임: 표준 재료 사전과 이름 매칭(재료), 구매 확정 입고 메시지(주문), 요리 완료 때 차감 요청(요리 기록).

## 동작 규칙
| 기능 | 규칙 | 근거 |
|---|---|---|
| 본인 소유 | 남의 재료와 없는 재료는 구분하지 않고 404 `FRIDGE_001`입니다 | `FridgeService.getFridgeEntity` |
| 표준 재료 매핑 | 등록할 때 `rawName`을 `IngredientMatcher`로 표준 재료에 잇고, 사전에 없는 이름은 직접 입력 재료(`ING_CUSTOM`)로 잇습니다. 여러 개를 한 번에 매핑합니다 | `FridgeFacade.createItems` |
| 기본값 | 보관방식·소비기한을 비우면 이은 재료의 기본 보관방식과 `오늘 + defaultShelfDays`로 채웁니다 | `FridgeFacade.createItems` |
| 수정 | 보낸 칸만 바꾸고 null인 칸은 그대로 둡니다(수정 창을 연 사이 요리 차감으로 바뀐 수량을 덮지 않기 위해). `rawName`이 바뀌었을 때만 다시 매핑하고, 밀키트 줄은 이름을 바꿔도 다시 매핑하지 않습니다. 매핑(ES 검색)은 쓰기 트랜잭션 밖에서 합니다 | `FridgeFacade.updateItem` |
| 빼기 | `quantity`가 null이거나 남은 양 이상이면 전부 뺍니다. 실제로 뺀 양을 이력에 남깁니다. 밀키트는 정수 개수만 뺄 수 있습니다(`FRIDGE_005`) | `FridgeService.takeOutItem` |
| 0 보정·자동 삭제 | 뺀 뒤 남은 양이 0.05 미만(`ZERO_THRESHOLD`)이면 0으로 보고, 0이 된 줄은 `fridge`에서 지웁니다 | `Fridge` |
| 비우기 이력 | 사유가 `CONSUMED`·`DISCARDED`면 이력을 남기고 지우고, `WRONG_ENTRY`는 이력 없이 지웁니다 | `FridgeRemoveReason` |
| 동시성 | 단건 수정·빼기·비우기는 `SELECT ... FOR UPDATE`로 잠그고 바꿉니다 | `FridgeService.getFridgeEntityForUpdate` |
| 상품 ID | 구매 확정으로 들어온 줄만 `fridge.product_id`가 있습니다. 직접 등록한 줄과 칸이 생기기 전의 예전 줄은 null입니다 | `FridgeFacade.stockPurchasedItems` |
| 이력 조회 | `from`·`to`는 `created_at` 날짜 기준이며 `to` 날짜 하루 전체를 포함합니다. `from`이 `to`보다 늦으면 400 `C001`입니다. 최신순입니다 | `FridgeService.getLogs` |

## 변경 이력
- `FridgeItemResponse.productId` 추가: 구매 확정 줄의 상품 ID. 상담 냉장고 보기의 밀키트 판별에 씁니다.
