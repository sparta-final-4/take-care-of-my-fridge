package org.example.takecareofmyfridgebe.domain.sale.repository;

import static org.example.takecareofmyfridgebe.domain.order.entity.QOrder.order;
import static org.example.takecareofmyfridgebe.domain.order.entity.QOrderItem.orderItem;
import static org.example.takecareofmyfridgebe.domain.product.entity.QProduct.product;
import static org.example.takecareofmyfridgebe.domain.sale.entity.QSaleEvent.saleEvent;
import static org.example.takecareofmyfridgebe.domain.sale.entity.QSaleEventItem.saleEventItem;

import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.DateTimePath;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.JPQLQuery;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderStatus;
import org.example.takecareofmyfridgebe.domain.sale.dto.SaleEventListRequest;
import org.example.takecareofmyfridgebe.domain.sale.dto.SaleEventListResponse;
import org.example.takecareofmyfridgebe.domain.sale.dto.SaleEventListSort;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventStatus;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.support.PageableExecutionUtils;

@RequiredArgsConstructor
public class SaleEventRepositoryImpl implements SaleEventRepositoryCustom {

    private static final char LIKE_ESCAPE = '\\';

    private final JPAQueryFactory queryFactory;

    // 비어 있는 조건(null)은 QueryDSL where에서 무시된다.
    @Override
    public Page<SaleEventListResponse> findSaleEvents(SaleEventListRequest condition, Pageable pageable) {
        BooleanExpression[] where = {
                statusEq(condition.status()),
                typeEq(condition.type()),
                nameContains(condition.keyword()),
                dateBetween(saleEvent.startsAt, condition.startFrom(), condition.startTo()),
                dateBetween(saleEvent.endsAt, condition.endFrom(), condition.endTo())
        };

        List<SaleEventListResponse> content = queryFactory
                .select(Projections.constructor(SaleEventListResponse.class,
                        saleEvent.id,
                        saleEvent.type,
                        saleEvent.status,
                        saleEvent.startsAt,
                        saleEvent.endsAt,
                        product.id,
                        product.name,
                        listPrice(),
                        saleEventItem.salePrice,
                        saleEventItem.discountRate,
                        saleEventItem.limitedQuantity,
                        saleEventItem.remainingQuantity,
                        soldQuantity()))
                .from(saleEventItem)
                .join(saleEventItem.saleEvent, saleEvent)
                .join(product).on(product.id.eq(saleEventItem.productId))
                .where(where)
                .orderBy(orderBy(condition))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(saleEventItem.count())
                .from(saleEventItem)
                .join(saleEventItem.saleEvent, saleEvent)
                .join(product).on(product.id.eq(saleEventItem.productId))
                .where(where);

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    // 종료된 행사는 등록 때 저장한 정가, 예정·진행 중 행사는 상품의 현재 정가를 보여 준다.
    private NumberExpression<Integer> listPrice() {
        return new CaseBuilder()
                .when(saleEvent.status.eq(SaleEventStatus.ENDED)).then(saleEventItem.listPrice)
                .otherwise(product.price);
    }

    // 결제 완료·구매 확정 주문의 수량만 센다. 취소·결제 대기 주문은 팔린 것으로 보지 않는다.
    private JPQLQuery<Long> soldQuantity() {
        return JPAExpressions.select(orderItem.quantity.longValue().sum().coalesce(0L))
                .from(orderItem)
                .join(orderItem.order, order)
                .where(orderItem.saleEventItemId.eq(saleEventItem.id),
                        order.status.in(OrderStatus.PAID, OrderStatus.CONFIRMED));
    }

    // 기준이 같은 행은 최신 행사가 먼저, 같은 행사 안에서는 먼저 담은 상품이 먼저 와서 페이지를 넘겨도 순서가 흔들리지 않는다.
    private OrderSpecifier<?>[] orderBy(SaleEventListRequest condition) {
        OrderSpecifier<Long> newestFirst = saleEvent.id.desc();
        OrderSpecifier<Long> itemOrder = saleEventItem.id.asc();
        SaleEventListSort sort = condition.sort();
        if (sort == null) {
            return new OrderSpecifier<?>[] {newestFirst, itemOrder};
        }
        Order order = condition.direction() == Sort.Direction.ASC ? Order.ASC : Order.DESC;
        OrderSpecifier<?> primary = switch (sort) {
            case PRODUCT_NAME -> new OrderSpecifier<>(order, product.name);
            case TYPE -> new OrderSpecifier<>(order, saleEvent.type);
            case STATUS -> new OrderSpecifier<>(order, statusStep());
            case LIST_PRICE -> new OrderSpecifier<>(order, listPrice());
            case SALE_PRICE -> new OrderSpecifier<>(order, saleEventItem.salePrice);
            case DISCOUNT_RATE -> new OrderSpecifier<>(order, saleEventItem.discountRate);
            case LIMITED_QUANTITY -> new OrderSpecifier<>(order, saleEventItem.limitedQuantity);
            case REMAINING_QUANTITY -> new OrderSpecifier<>(order, saleEventItem.remainingQuantity);
            case SOLD_QUANTITY -> new OrderSpecifier<>(order, soldQuantity());
            case STARTS_AT -> new OrderSpecifier<>(order, saleEvent.startsAt);
            case ENDS_AT -> new OrderSpecifier<>(order, saleEvent.endsAt);
        };
        return new OrderSpecifier<?>[] {primary, newestFirst, itemOrder};
    }

    // 상태는 이름순이 아니라 예정, 진행 중, 종료 순서로 정렬한다.
    private NumberExpression<Integer> statusStep() {
        return new CaseBuilder()
                .when(saleEvent.status.eq(SaleEventStatus.SCHEDULED)).then(0)
                .when(saleEvent.status.eq(SaleEventStatus.ACTIVE)).then(1)
                .otherwise(2);
    }

    private BooleanExpression nameContains(String keyword) {
        if (keyword == null) {
            return null;
        }
        return product.name.likeIgnoreCase("%" + escapeLike(keyword) + "%", LIKE_ESCAPE);
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private BooleanExpression statusEq(SaleEventStatus status) {
        return status == null ? null : saleEvent.status.eq(status);
    }

    private BooleanExpression typeEq(SaleEventType type) {
        return type == null ? null : saleEvent.type.eq(type);
    }

    // from의 0시 이상, to 다음 날 0시 미만. 날짜 끝 하루가 통째로 들어간다.
    private BooleanExpression dateBetween(DateTimePath<LocalDateTime> path, LocalDate from, LocalDate to) {
        if (from == null) {
            return to == null ? null : path.lt(to.plusDays(1).atStartOfDay());
        }
        BooleanExpression afterFrom = path.goe(from.atStartOfDay());
        return to == null ? afterFrom : afterFrom.and(path.lt(to.plusDays(1).atStartOfDay()));
    }
}
