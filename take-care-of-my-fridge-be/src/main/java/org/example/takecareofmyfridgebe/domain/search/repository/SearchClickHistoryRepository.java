package org.example.takecareofmyfridgebe.domain.search.repository;

import org.example.takecareofmyfridgebe.domain.search.dto.ProductClickCount;
import org.example.takecareofmyfridgebe.domain.search.entity.SearchClickHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SearchClickHistoryRepository extends JpaRepository<SearchClickHistory, Long> {

    // createdAt이 [from, to) 범위이고 asOf 이전인 클릭을 상품별로 센다
    @Query("""
        select new org.example.takecareofmyfridgebe.domain.search.dto.ProductClickCount(
            h.productId, count(h))
        from SearchClickHistory h
        where h.createdAt >= :from and h.createdAt < :to
          and h.createdAt <= :asOf
        group by h.productId
        order by h.productId
        """)
    List<ProductClickCount> countClicksByProduct(
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to,
        @Param("asOf") LocalDateTime asOf
    );
}
