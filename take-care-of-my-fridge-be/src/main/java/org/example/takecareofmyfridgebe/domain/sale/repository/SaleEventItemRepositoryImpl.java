package org.example.takecareofmyfridgebe.domain.sale.repository;

import static org.example.takecareofmyfridgebe.domain.product.entity.QProduct.product;
import static org.example.takecareofmyfridgebe.domain.sale.entity.QSaleEvent.saleEvent;
import static org.example.takecareofmyfridgebe.domain.sale.entity.QSaleEventItem.saleEventItem;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductType;
import org.example.takecareofmyfridgebe.domain.sale.dto.SaleCandidateProductsRequest;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEvent;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;

@RequiredArgsConstructor
public class SaleEventItemRepositoryImpl implements SaleEventItemRepositoryCustom {

    private static final char LIKE_ESCAPE = '\\';

    private final JPAQueryFactory queryFactory;

    // 비어 있는 조건(null)은 QueryDSL where에서 무시된다.
    @Override
    public Page<Product> findCandidateProducts(SaleCandidateProductsRequest condition, Pageable pageable) {
        BooleanExpression[] where = {
                product.status.eq(ProductStatus.ON_SALE),
                product.stock.gt(0),
                notInOpenSaleEvent(),
                nameContains(condition.keyword()),
                typeEq(condition.type())
        };

        List<Product> content = queryFactory
                .selectFrom(product)
                .where(where)
                .orderBy(product.id.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(product.count())
                .from(product)
                .where(where);

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    // 상태 정리가 늦어도 같은 결과가 나오도록 종료 시각도 함께 본다.
    private BooleanExpression notInOpenSaleEvent() {
        return JPAExpressions.selectOne()
                .from(saleEventItem)
                .join(saleEventItem.saleEvent, saleEvent)
                .where(saleEventItem.productId.eq(product.id), saleEvent.status.in(SaleEventStatus.OPEN),
                        saleEvent.endsAt.gt(LocalDateTime.now(SaleEvent.ZONE)))
                .notExists();
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

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
