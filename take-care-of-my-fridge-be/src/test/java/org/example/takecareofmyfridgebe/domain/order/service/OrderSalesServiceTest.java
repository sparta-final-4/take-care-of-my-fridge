package org.example.takecareofmyfridgebe.domain.order.service;

import org.example.takecareofmyfridgebe.domain.order.dto.DailyProductSales;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderStatus;
import org.example.takecareofmyfridgebe.domain.order.repository.OrderItemRepository;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class OrderSalesServiceTest {

    private static final LocalDateTime AS_OF = LocalDateTime.of(2026, 10, 4, 3, 0);

    @Mock
    private OrderItemRepository orderItemRepository;

    @InjectMocks
    private OrderSalesService orderSalesService;

    @Test
    @DisplayName("시작일 0시부터 종료일 다음 날 0시까지 조회한다")
    void queriesFromStartOfFromToStartOfDayAfterTo() {
        List<DailyProductSales> sales = List.of(new DailyProductSales(1L, LocalDate.of(2026, 10, 2), 5L));
        given(orderItemRepository.findDailyProductSales(OrderStatus.CONFIRMED,
            LocalDateTime.of(2026, 10, 1, 0, 0), LocalDateTime.of(2026, 10, 4, 0, 0), AS_OF)).willReturn(sales);

        List<DailyProductSales> result =
            orderSalesService.getDailyProductSales(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3), AS_OF);

        assertThat(result).isEqualTo(sales);
    }

    @Test
    @DisplayName("시작일이 종료일보다 늦으면 거절한다")
    void rejectsFromAfterTo() {
        assertThatThrownBy(() ->
            orderSalesService.getDailyProductSales(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 1), AS_OF))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("기준 시각이 없으면 거절한다")
    void rejectsMissingAsOf() {
        assertThatThrownBy(() ->
            orderSalesService.getDailyProductSales(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3), null))
            .isInstanceOf(BusinessException.class);
    }
}
