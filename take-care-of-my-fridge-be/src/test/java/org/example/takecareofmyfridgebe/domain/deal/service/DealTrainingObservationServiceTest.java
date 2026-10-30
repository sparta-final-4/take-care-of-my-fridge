package org.example.takecareofmyfridgebe.domain.deal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.example.takecareofmyfridgebe.domain.order.dto.DailyProductSales;
import org.example.takecareofmyfridgebe.domain.order.dto.SaleEventItemQuantity;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderStatus;
import org.example.takecareofmyfridgebe.domain.order.repository.OrderItemRepository;
import org.example.takecareofmyfridgebe.domain.order.service.OrderSalesService;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEvent;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventItem;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventType;
import org.example.takecareofmyfridgebe.domain.sale.repository.SaleEventItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DealTrainingObservationServiceTest {

    private static final LocalDateTime AS_OF = LocalDateTime.of(2026, 11, 5, 12, 0);
    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 28, 9, 0);
    private static final LocalDateTime END = START.plusHours(12);

    @Mock private SaleEventItemRepository saleEventItemRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private OrderSalesService orderSalesService;
    @InjectMocks private DealTrainingObservationService service;

    @Test
    void usesOnlyEndedSpecialSalesAndScalesRegularSalesByEventHours() {
        SaleEventItem item = item();
        given(saleEventItemRepository.findFinishedForTraining(SaleEventType.SPECIAL, AS_OF.minusDays(7)))
                .willReturn(List.of(item));
        given(orderSalesService.getDailyRegularProductSales(
                LocalDate.of(2026, 9, 30), LocalDate.of(2026, 10, 27), AS_OF))
                .willReturn(List.of(
                        sale(2026, 9, 30), sale(2026, 10, 7),
                        sale(2026, 10, 14), sale(2026, 10, 21)));
        given(orderItemRepository.sumConfirmedSaleQuantities(List.of(2L), OrderStatus.CONFIRMED, AS_OF))
                .willReturn(List.of(new SaleEventItemQuantity(2L, 5L)));

        assertThat(service.getObservations(AS_OF))
                .containsExactly(new DealDiscountModel.Observation(20, 2.0, 5.0));
        verify(orderItemRepository).findSaleEventItemIdsWithUnconfirmedPaidOrders(List.of(2L), OrderStatus.PAID, AS_OF);
    }

    @Test
    void skipsEventWithUnconfirmedPaidOrder() {
        SaleEventItem item = item();
        given(saleEventItemRepository.findFinishedForTraining(SaleEventType.SPECIAL, AS_OF.minusDays(7)))
                .willReturn(List.of(item));
        given(orderItemRepository.findSaleEventItemIdsWithUnconfirmedPaidOrders(List.of(2L), OrderStatus.PAID, AS_OF))
                .willReturn(List.of(2L));

        assertThat(service.getObservations(AS_OF)).isEmpty();
        verifyNoInteractions(orderSalesService);
    }

    @Test
    void countsRegularSalesOnlyUntilEventWasEndedEarly() {
        SaleEventItem item = item();
        item.getSaleEvent().end(START.plusHours(3));
        given(saleEventItemRepository.findFinishedForTraining(SaleEventType.SPECIAL, AS_OF.minusDays(7)))
                .willReturn(List.of(item));
        given(orderSalesService.getDailyRegularProductSales(
                LocalDate.of(2026, 9, 30), LocalDate.of(2026, 10, 27), AS_OF))
                .willReturn(List.of(
                        sale(2026, 9, 30), sale(2026, 10, 7),
                        sale(2026, 10, 14), sale(2026, 10, 21)));
        given(orderItemRepository.sumConfirmedSaleQuantities(List.of(2L), OrderStatus.CONFIRMED, AS_OF))
                .willReturn(List.of(new SaleEventItemQuantity(2L, 1L)));

        // 12시간 행사를 3시간 만에 닫았으므로 정상 판매 기대량은 하루 4개의 3/24다
        assertThat(service.getObservations(AS_OF))
                .containsExactly(new DealDiscountModel.Observation(20, 0.5, 1.0));
    }

    private SaleEventItem item() {
        SaleEvent event = SaleEvent.builder()
                .type(SaleEventType.SPECIAL).startsAt(START).endsAt(END).build();
        ReflectionTestUtils.setField(event, "id", 1L);
        SaleEventItem item = SaleEventItem.builder()
                .saleEvent(event).productId(10L).listPrice(1000).discountRate(20).build();
        ReflectionTestUtils.setField(item, "id", 2L);
        return item;
    }

    private DailyProductSales sale(int year, int month, int day) {
        return new DailyProductSales(10L, LocalDate.of(year, month, day), 4L);
    }
}
