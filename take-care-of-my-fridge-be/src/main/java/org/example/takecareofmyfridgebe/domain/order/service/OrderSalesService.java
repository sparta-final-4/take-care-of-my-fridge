package org.example.takecareofmyfridgebe.domain.order.service;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.order.dto.DailyProductSales;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderStatus;
import org.example.takecareofmyfridgebe.domain.order.repository.OrderItemRepository;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// 판매 예측(deal)이 쓰는 날짜·상품별 판매량 조회.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderSalesService {

    private final OrderItemRepository orderItemRepository;

    // 판매 예측용: from~to(양끝 포함) 기간의 구매 확정 판매량을 날짜·상품별로 반환한다.
    // asOf 이후에 확정된 주문은 빼서, 같은 asOf로 다시 조회하면 같은 결과가 나온다.
    // 확정 수량은 확정 전에 완료된 환불을 이미 뺀 값이고 확정 뒤에는 환불이 없으므로, 환불을 따로 빼지 않아도 순판매 수량이다.
    public List<DailyProductSales> getDailyRegularProductSales(LocalDate from, LocalDate to, LocalDateTime asOf) {
        validatePeriod(from, to, asOf);
        return orderItemRepository.findDailyRegularProductSales(
                OrderStatus.CONFIRMED, from.atStartOfDay(), to.plusDays(1).atStartOfDay(), asOf);
    }

    public List<DailyProductSales> getDailyProductSales(LocalDate from, LocalDate to, LocalDateTime asOf) {
        validatePeriod(from, to, asOf);
        return orderItemRepository.findDailyProductSales(
                OrderStatus.CONFIRMED, from.atStartOfDay(), to.plusDays(1).atStartOfDay(), asOf);
    }

    private void validatePeriod(LocalDate from, LocalDate to, LocalDateTime asOf) {
        if (from == null || to == null || asOf == null || from.isAfter(to)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
