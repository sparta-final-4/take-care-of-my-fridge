package org.example.takecareofmyfridgebe.domain.fridge.facade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeItemCreateRequest;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeItemResponse;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeItemUpdateRequest;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeNameInfo;
import org.example.takecareofmyfridgebe.domain.fridge.entity.Fridge;
import org.example.takecareofmyfridgebe.domain.fridge.event.PurchaseConfirmedMessage;
import org.example.takecareofmyfridgebe.domain.fridge.service.FridgeService;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.Ingredient;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;
import org.example.takecareofmyfridgebe.domain.ingredient.service.IngredientMatcher;
import org.example.takecareofmyfridgebe.domain.ingredient.service.IngredientService;
import org.example.takecareofmyfridgebe.domain.product.dto.ProductDetail;
import org.example.takecareofmyfridgebe.domain.product.entity.IngredientProduct;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductType;
import org.example.takecareofmyfridgebe.domain.product.service.ProductService;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class FridgeFacade {

    private final IngredientMatcher ingredientMatcher;
    private final IngredientService ingredientService;
    private final ProductService productService;
    private final FridgeService fridgeService;

    /**
     * [냉장고 재료 매핑 및 등록]
     * ingredient 도메인(표준 재료 매핑)과 fridge 도메인(냉장고 저장)을 조율한다.
     * 추후 Elasticsearch 외부 I/O가 추가되는 매핑 단계를 DB 트랜잭션 밖에 두기 위해
     * Facade에는 트랜잭션을 걸지 않고, 엔티티 조립 후 FridgeService.saveItems에서 한 트랜잭션으로 저장한다.
     *
     * @param memberId 로그인한 회원 ID
     * @param request  등록할 냉장고 재료 요청 목록
     * @return 등록된 냉장고 재료 응답 목록
     */
    public List<FridgeItemResponse> createItems(Long memberId, FridgeItemCreateRequest request) {
        LocalDate today = LocalDate.now();

        // 등록 항목 수와 관계없이 재료 조회 쿼리를 일괄 처리하기 위해 한 번에 매핑
        Map<String, Ingredient> ingredients = ingredientMatcher.matchAll(
                request.items().stream()
                        .map(FridgeItemCreateRequest.Item::rawName)
                        .toList()
        );

        List<Fridge> fridges = request.items().stream()
                .map(item -> {
                    Ingredient ingredient = ingredients.get(item.rawName());
                    StorageType storage = item.storage() != null
                            ? item.storage()
                            : ingredient.getDefaultStorage();
                    LocalDate expirationDate = item.expirationDate() != null
                            ? item.expirationDate()
                            : ingredient.calculateExpirationDate(today);
                    return item.toEntity(memberId, ingredient, storage, expirationDate);
                })
                .toList();

        return fridgeService.saveItems(fridges);
    }

    /**
     * [냉장고 재료 수정 및 선택적 재매핑]
     * 본인 재료 여부를 먼저 확인한 뒤, 입력된 rawName이 기존과 다를 때만 표준 재료를 다시 매핑한다.
     * 단, 밀키트(ING_MEALKIT) 재료는 이름을 바꿔도 다시 매핑하지 않고 밀키트로 유지한다.
     * 추후 Elasticsearch 외부 I/O가 추가되는 매핑 단계를 DB 쓰기 트랜잭션 밖에 두기 위해
     * Facade에는 트랜잭션을 걸지 않고, 매핑 확정 후 FridgeService.updateItem에서 수정한다.
     *
     * @param memberId 로그인한 회원 ID
     * @param fridgeId 수정할 냉장고 재료 ID
     * @param request  수정할 냉장고 재료 요청 정보
     * @return 수정된 냉장고 재료 응답
     */
    public FridgeItemResponse updateItem(Long memberId, Long fridgeId, FridgeItemUpdateRequest request) {
        // 이름 변경 여부만 확인하는 비교용 조회 (재매핑을 트랜잭션 밖에서 하기 위해 분리)
        FridgeNameInfo current = fridgeService.getNameInfo(memberId, fridgeId);

        // 밀키트이거나, 이름을 안 보냈거나, 같은 이름이면 null을 넘겨 FridgeService.updateItem에서 기존 재료를 유지한다.
        boolean keepIngredient = current.mealkit()
                || request.rawName() == null
                || current.rawName().equals(request.rawName());
        Ingredient ingredient = keepIngredient ? null : ingredientMatcher.match(request.rawName());

        return fridgeService.updateItem(memberId, fridgeId, ingredient, request);
    }

    /**
     * [구매 확정 상품 냉장고 입고]
     * 구매 확정 메시지의 주문 상품을 냉장고에 넣는다. product 도메인(상품·상세)과 ingredient 도메인(재료)을 조율한다.
     * 조회는 트랜잭션 밖에서 하고, 주문 한 건을 FridgeService.stockCommerceItems 한 트랜잭션으로 저장한다.
     * 이미 입고된 주문 상품은 먼저 걸러 내, 같은 메시지를 다시 받아도 두 번 넣지 않는다.
     * 재료 상품은 연결된 재료로 확정 수량 × 상품 1개의 기본 단위 수량만큼, 밀키트는 밀키트용 기본 재료(ING_MEALKIT)로 확정 수량(개수)만큼 넣는다.
     * 보관방식은 상품 보관 방법, 소비기한은 구매 확정일 + 상품 권장 보관 일수다.
     *
     * @param message 구매 확정 메시지
     * @throws BusinessException 상품이나 상품에 연결된 재료를 찾지 못한 경우 (재시도 뒤 DLT로 간다)
     */
    public void stockPurchasedItems(PurchaseConfirmedMessage message) {
        List<PurchaseConfirmedMessage.Item> items = message.items() == null ? List.of() : message.items();
        if (items.isEmpty()) {
            return;
        }

        Set<Long> stockedOrderItemIds = fridgeService.getStockedOrderItemIds(
                items.stream().map(PurchaseConfirmedMessage.Item::orderItemId).toList()
        );
        List<PurchaseConfirmedMessage.Item> targets = items.stream()
                .filter(item -> !stockedOrderItemIds.contains(item.orderItemId()))
                .toList();
        int skippedCount = items.size() - targets.size();
        if (targets.isEmpty()) {
            log.info("이미 입고된 구매 확정 건너뜀: orderId={}, memberId={}, skipped={}",
                    message.orderId(), message.memberId(), skippedCount);
            return;
        }

        Map<Long, ProductDetail> products = productService.getProductDetailsByIds(
                        targets.stream().map(PurchaseConfirmedMessage.Item::productId).distinct().toList()
                ).stream()
                .collect(Collectors.toMap(detail -> detail.product().getId(), Function.identity()));
        Map<Long, Ingredient> ingredients = ingredientService.getIngredientsByIds(
                products.values().stream()
                        .map(ProductDetail::ingredient)
                        .filter(Objects::nonNull)
                        .map(IngredientProduct::getIngredientId)
                        .collect(Collectors.toSet())
        );
        // 밀키트가 없는 주문은 밀키트용 기본 재료를 조회하지 않는다
        Ingredient mealkitIngredient = products.values().stream()
                .anyMatch(detail -> detail.product().getProductType() == ProductType.MEALKIT)
                ? ingredientService.getMealkitIngredient()
                : null;

        LocalDate confirmedDate = message.confirmedAt().toLocalDate();
        List<Fridge> fridges = targets.stream()
                .map(item -> toCommerceFridge(message, item, products.get(item.productId()),
                        ingredients, mealkitIngredient, confirmedDate))
                .toList();

        fridgeService.stockCommerceItems(fridges);
        log.info("구매 확정 입고 완료: orderId={}, memberId={}, stocked={}, skipped={}",
                message.orderId(), message.memberId(), fridges.size(), skippedCount);
    }

    private Fridge toCommerceFridge(
            PurchaseConfirmedMessage message,
            PurchaseConfirmedMessage.Item item,
            ProductDetail detail,
            Map<Long, Ingredient> ingredients,
            Ingredient mealkitIngredient,
            LocalDate confirmedDate
    ) {
        if (detail == null) {
            throw stockFailure(message, item, ErrorCode.PRODUCT_NOT_FOUND);
        }
        Product product = detail.product();
        LocalDate expirationDate = confirmedDate.plusDays(product.getShelfDays());

        return switch (product.getProductType()) {
            case INGREDIENT -> {
                IngredientProduct link = detail.ingredient();
                Ingredient ingredient = link == null ? null : ingredients.get(link.getIngredientId());
                if (ingredient == null) {
                    throw stockFailure(message, item, ErrorCode.PRODUCT_DETAIL_MISSING);
                }
                BigDecimal quantity = link.getBaseQuantity().multiply(BigDecimal.valueOf(item.confirmedQuantity()));
                yield item.toEntity(message.memberId(), ingredient, quantity, product.getStorage(), expirationDate);
            }
            case MEALKIT -> item.toEntity(message.memberId(), mealkitIngredient,
                    BigDecimal.valueOf(item.confirmedQuantity()), product.getStorage(), expirationDate);
        };
    }

    // 재시도마다 남지만, DLT로 간 메시지의 원인을 주문·상품 ID로 찾을 수 있게 한다
    private BusinessException stockFailure(
            PurchaseConfirmedMessage message,
            PurchaseConfirmedMessage.Item item,
            ErrorCode errorCode
    ) {
        log.error("구매 확정 입고 실패, 상품 또는 연결된 재료 없음: orderId={}, productId={}",
                message.orderId(), item.productId());
        return new BusinessException(errorCode);
    }
}
