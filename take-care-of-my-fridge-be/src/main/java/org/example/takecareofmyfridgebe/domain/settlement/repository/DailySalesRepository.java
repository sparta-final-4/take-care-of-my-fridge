package org.example.takecareofmyfridgebe.domain.settlement.repository;

import org.example.takecareofmyfridgebe.domain.settlement.entity.DailySales;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailySalesRepository extends JpaRepository<DailySales, Long> {

    // 스케줄러가 집계할 날의 행이 이미 있으면 다시 집계해 덮어쓴다
    Optional<DailySales> findBySalesDate(LocalDate salesDate);

    // 정산 표·차트용. 시작일과 종료일을 모두 포함하고, 집계되지 않은 날은 행이 없다
    List<DailySales> findBySalesDateBetweenOrderBySalesDateAsc(LocalDate startDate, LocalDate endDate);

    // 정산 조회가 빈 날을 찾을 때 쓴다. 엔티티를 읽지 않고 날짜만 가져온다
    @Query("select d.salesDate from DailySales d where d.salesDate between :startDate and :endDate")
    List<LocalDate> findSalesDatesBetween(@Param("startDate") LocalDate startDate,
                                          @Param("endDate") LocalDate endDate);
}
