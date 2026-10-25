package org.example.takecareofmyfridgebe.domain.sale.repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.example.takecareofmyfridgebe.domain.sale.dto.ActiveSale;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventItem;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SaleEventItemRepository extends JpaRepository<SaleEventItem, Long>, SaleEventItemRepositoryCustom {

    // 주어진 상품 중 종료되지 않았고 종료 시각이 지나지 않은 행사(예정·진행 중)에 이미 든 상품 ID.
    // 상태 정리가 늦어도 같은 결과가 나오도록 종료 시각도 함께 본다.
    @Query("""
        select i.productId from SaleEventItem i join i.saleEvent e
        where i.productId in :productIds and e.status in :statuses and e.endsAt > :now
        """)
    List<Long> findProductIdsInOpenSaleEvent(
        @Param("productIds") Collection<Long> productIds,
        @Param("statuses") Collection<SaleEventStatus> statuses,
        @Param("now") LocalDateTime now
    );

    // 진행 중인 행사의 상품. 상태가 아니라 시간(시작 ≤ 지금 < 종료)으로 판단하고, 종료로 닫은 행사만 뺀다.
    // 열기·닫기 스케줄러가 늦어도 시작·종료가 그 시각에 맞게 반영된다.
    // 엔티티가 아니라 값으로 읽는다. 엔티티로 읽으면 영속성 컨텍스트에 남아, 뒤이은 잠금 조회가 최신 남은 수량 대신 이 시점의 값을 돌려준다.
    @Query("""
        select new org.example.takecareofmyfridgebe.domain.sale.dto.ActiveSale(
            i.productId, i.id, e.type, i.salePrice, i.discountRate, e.endsAt, i.remainingQuantity, i.limitedQuantity)
        from SaleEventItem i join i.saleEvent e
        where i.productId in :productIds and e.status in :statuses
          and e.startsAt <= :now and e.endsAt > :now
        """)
    List<ActiveSale> findActiveByProductIds(
        @Param("productIds") Collection<Long> productIds,
        @Param("statuses") Collection<SaleEventStatus> statuses,
        @Param("now") LocalDateTime now
    );

    // 행사에 든 상품을 먼저 담은 순서로 읽는다.
    List<SaleEventItem> findBySaleEventIdOrderById(Long saleEventId);

    // 번개 드롭 남은 수량을 바꾸기 전에 행을 잠근다. 잠그는 순서가 엇갈리지 않도록 ID 순으로 읽는다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from SaleEventItem i where i.id in :ids order by i.id")
    List<SaleEventItem> findAllByIdInForUpdate(@Param("ids") Collection<Long> ids);
}
