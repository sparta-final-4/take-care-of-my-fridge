package org.example.takecareofmyfridgebe.domain.sale.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventItem;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
