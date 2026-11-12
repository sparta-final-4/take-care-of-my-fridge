package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate.MissingIngredient;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate.Ranking;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate.Reason;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;
import org.example.takecareofmyfridgebe.domain.member.entity.Member.Taste;
import org.example.takecareofmyfridgebe.domain.product.entity.IngredientProduct;
import org.example.takecareofmyfridgebe.domain.product.entity.MealkitProduct;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductType;
import org.example.takecareofmyfridgebe.domain.product.service.ProductService;
import org.example.takecareofmyfridgebe.domain.sale.dto.ActiveSale;
import org.example.takecareofmyfridgebe.domain.sale.dto.SellingPrice;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventType;
import org.example.takecareofmyfridgebe.domain.sale.service.SaleEventReader;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 추천 카드 테스트(새 추천 ChatRecommendationFacadeTest, 다시 만들기 ChatCardRebuilderTest)가 같이 쓰는 후보·상품·밀키트 만들기.
 */
final class RecommendationCardFixture {

    private RecommendationCardFixture() {
    }

    record MealkitWithPrice(MealkitProduct mealkit, SellingPrice price) {
    }

    static MealkitWithPrice mealkit(long productId, String name, int price, Long recipeId) {
        return mealkitWith(productId, name, price, recipeId, ProductStatus.ON_SALE);
    }

    static MealkitWithPrice soldOutMealkit(long productId, String name, int price) {
        return mealkitWith(productId, name, price, 539L, ProductStatus.SOLD_OUT);
    }

    static MealkitWithPrice mealkitWith(long productId, String name, int price, Long recipeId, ProductStatus status) {
        Product product = Product.builder().productType(ProductType.MEALKIT).name(name).price(price)
                .stock(status == ProductStatus.SOLD_OUT ? 0 : 10).storage(StorageType.REFRIGERATED).shelfDays(3).status(status)
                .build();
        ReflectionTestUtils.setField(product, "id", productId);
        MealkitProduct mealkit = MealkitProduct.builder().product(product).servings(2).recipeId(recipeId).build();
        return new MealkitWithPrice(mealkit, SellingPrice.of(product, null));
    }

    // 장볼 재료만 있는 근거
    static Reason missing(MissingIngredient... ingredients) {
        return new Reason(List.of(), List.of(), List.of(), List.of(), List.of(), false, false, List.of(ingredients),
                List.of(), List.of(), null);
    }

    // 상품 조회는 받은 연결을 돌려주고, 판매가는 상품마다 계산해 둔 값을 돌려준다
    static void givenProducts(ProductService productService, SaleEventReader saleEventReader,
                              ProductWithPrice... products) {
        when(productService.getPublicIngredientProducts(any()))
                .thenReturn(Arrays.stream(products).map(ProductWithPrice::link).toList());
        Map<Long, SellingPrice> prices = new HashMap<>();
        for (ProductWithPrice product : products) {
            prices.put(product.link().getProduct().getId(), product.price());
        }
        when(saleEventReader.findSellingPrices(any())).thenReturn(prices);
    }

    record ProductWithPrice(IngredientProduct link, SellingPrice price) {
    }

    static ProductWithPrice product(long productId, long ingredientId, String name, int price, Integer salePrice) {
        Product product = productEntity(productId, name, price, ProductStatus.ON_SALE);
        ActiveSale sale = salePrice == null ? null
                : new ActiveSale(productId, 900L, SaleEventType.SPECIAL, salePrice, 25, LocalDateTime.now().plusDays(1), null, null);
        return new ProductWithPrice(link(product, ingredientId), SellingPrice.of(product, sale));
    }

    static ProductWithPrice soldOutProduct(long productId, long ingredientId, String name, int price) {
        Product product = productEntity(productId, name, price, ProductStatus.SOLD_OUT);
        return new ProductWithPrice(link(product, ingredientId), SellingPrice.of(product, null));
    }

    static Product productEntity(long productId, String name, int price, ProductStatus status) {
        Product product = Product.builder().productType(ProductType.INGREDIENT).name(name).price(price)
                .stock(status == ProductStatus.SOLD_OUT ? 0 : 10).storage(StorageType.REFRIGERATED).shelfDays(7).status(status)
                .build();
        ReflectionTestUtils.setField(product, "id", productId);
        return product;
    }

    static IngredientProduct link(Product product, long ingredientId) {
        return IngredientProduct.builder().product(product).ingredientId(ingredientId).unitAmount(BigDecimal.ONE).unit("개")
                .baseQuantity(BigDecimal.ONE).build();
    }

    static RecipeCandidate candidate(Long id, String name, String category, Integer cookTimeMin, Reason reason) {
        return new RecipeCandidate(id, name, category, cookTimeMin, new Ranking(0, 0, 0, 0, 1, 1, 0), reason);
    }

    static Reason reason(List<Taste> tastes) {
        return new Reason(List.of(), List.of(), tastes, List.of(), List.of(), false, false, List.of(), List.of(), List.of(), null);
    }
}
