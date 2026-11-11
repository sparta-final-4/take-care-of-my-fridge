package org.example.takecareofmyfridgebe.domain.chat.recommend;

import org.example.takecareofmyfridgebe.domain.chat.dto.ChatRecommendationsEventResponse.ShoppingProduct;
import org.example.takecareofmyfridgebe.domain.product.entity.IngredientProduct;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.sale.dto.SellingPrice;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 장볼 재료마다 담을 상품을 하나 고른다. 스프링에 의존하지 않는 순수 클래스.
 * 재료 하나에 상품이 여러 개면 살 수 있는 것(품절 아님) → 할인 중 → 판매 단가 낮은 순 → 상품 ID 순으로 고른다.
 * 판매가(할인가·품절)는 SaleEventReader가 계산한 값을 그대로 쓴다 (상품 화면·장바구니와 같은 기준).
 */
public final class ShoppingProductPicker {

    private static final Comparator<Option> ORDER = Comparator
            // false(살 수 있음)가 앞
            .comparing((Option option) -> option.price().soldOut())
            // true(할인 중)가 앞
            .thenComparing((Option option) -> option.price().sale().isEmpty())
            .thenComparingInt(option -> option.price().unitPrice())
            .thenComparing(option -> option.link().getProduct().getId());

    private ShoppingProductPicker() {
    }

    /**
     * @param links  재료 상품 연결 (상품이 함께 읽혀 있어야 한다)
     * @param prices 상품 ID별 판매가. 판매가가 없는 상품은 고르지 않는다
     * @return 재료 ID별 담을 상품. 상품이 없는 재료는 맵에 없다
     */
    public static Map<Long, ShoppingProduct> pick(List<IngredientProduct> links, Map<Long, SellingPrice> prices) {
        return links.stream()
                .map(link -> Optional.ofNullable(prices.get(link.getProduct().getId())).map(price -> new Option(link, price)))
                .flatMap(Optional::stream)
                .collect(Collectors.groupingBy(option -> option.link().getIngredientId(), LinkedHashMap::new,
                        Collectors.collectingAndThen(Collectors.minBy(ORDER), best -> best.map(Option::toProduct).orElseThrow())));
    }

    private record Option(IngredientProduct link, SellingPrice price) {

        ShoppingProduct toProduct() {
            Product product = link.getProduct();
            return new ShoppingProduct(
                    product.getId(),
                    product.getName(),
                    product.getImageUrl(),
                    price.listPrice(),
                    price.sale().isPresent() ? price.unitPrice() : null,
                    price.saleType(),
                    price.soldOut(),
                    link.getUnitAmount(),
                    link.getUnit());
        }
    }
}
