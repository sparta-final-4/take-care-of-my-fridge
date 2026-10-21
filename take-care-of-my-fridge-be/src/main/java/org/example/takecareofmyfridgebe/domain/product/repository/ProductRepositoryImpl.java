package org.example.takecareofmyfridgebe.domain.product.repository;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.product.dto.ProductListRequest;
import org.example.takecareofmyfridgebe.domain.product.dto.ProductSort;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

import static org.example.takecareofmyfridgebe.domain.product.entity.QProduct.product;

@Repository
@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepositoryCustom {

    private static final char LIKE_ESCAPE = '\\';

    private final JPAQueryFactory queryFactory;

    // 비어 있는 조건(null)은 QueryDSL where에서 무시된다.
    @Override
    public Page<Product> findProductsByCondition(ProductListRequest condition, Collection<ProductStatus> statuses, Pageable pageable) {
        BooleanExpression[] where = {
                product.status.in(statuses),
                nameContains(condition.keyword()),
                typeEq(condition.type()),
                priceGoe(condition.minPrice()),
                priceLoe(condition.maxPrice())
        };

        List<Product> content = queryFactory
                .selectFrom(product)
                .where(where)
                .orderBy(orderSpecifiers(condition.sort()))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(product.count())
                .from(product)
                .where(where);

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    private BooleanExpression nameContains(String keyword) {
        if (keyword == null) {
            return null;
        }
        return product.name.likeIgnoreCase("%" + escapeLike(keyword) + "%", LIKE_ESCAPE);
    }

    private BooleanExpression typeEq(ProductType type) {
        return type == null ? null : product.productType.eq(type);
    }

    private BooleanExpression priceGoe(Integer minPrice) {
        return minPrice == null ? null : product.price.goe(minPrice);
    }

    private BooleanExpression priceLoe(Integer maxPrice) {
        return maxPrice == null ? null : product.price.loe(maxPrice);
    }

    // 같은 값끼리는 최신 상품(id 내림차순)이 먼저 오도록 2차 정렬을 둔다.
    private OrderSpecifier<?>[] orderSpecifiers(ProductSort sort) {
        return switch (sort) {
            case LATEST -> new OrderSpecifier<?>[]{product.id.desc()};
            case PRICE_ASC -> new OrderSpecifier<?>[]{product.price.asc(), product.id.desc()};
            case PRICE_DESC -> new OrderSpecifier<?>[]{product.price.desc(), product.id.desc()};
        };
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
