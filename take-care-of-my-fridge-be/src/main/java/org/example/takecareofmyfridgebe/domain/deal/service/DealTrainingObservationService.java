package org.example.takecareofmyfridgebe.domain.deal.service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.order.dto.DailyProductSales;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderStatus;
import org.example.takecareofmyfridgebe.domain.order.repository.OrderItemRepository;
import org.example.takecareofmyfridgebe.domain.order.service.OrderSalesService;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEvent;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventItem;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventType;
import org.example.takecareofmyfridgebe.domain.sale.repository.SaleEventItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DealTrainingObservationService {

    private final SaleEventItemRepository saleEventItemRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderSalesService orderSalesService;

    public List<DealDiscountModel.Observation> getObservations(LocalDateTime asOf) {
        Map<Long, List<DailyProductSales>> regularSalesByEvent = new HashMap<>();
        return saleEventItemRepository.findFinishedForTraining(SaleEventType.SPECIAL, asOf.minusDays(7))
                .stream()
                .filter(item -> orderItemRepository.countUnconfirmedPaidSaleOrders(
                        item.getId(), OrderStatus.PAID, asOf) == 0)
                .map(item -> observation(item, asOf, regularSalesByEvent))
                .filter(observation -> observation.expectedRegularQuantity() > 0)
                .toList();
    }

    private DealDiscountModel.Observation observation(SaleEventItem item, LocalDateTime asOf,
            Map<Long, List<DailyProductSales>> regularSalesByEvent) {
        SaleEvent event = item.getSaleEvent();
        List<DailyProductSales> regularSales = regularSalesByEvent.computeIfAbsent(event.getId(), id ->
                orderSalesService.getDailyRegularProductSales(
                        event.getStartsAt().toLocalDate().minusWeeks(4),
                        event.getStartsAt().toLocalDate().minusDays(1), asOf));
        Map<DayOfWeek, Long> quantityByWeekday = new HashMap<>();
        regularSales.stream()
                .filter(sale -> sale.productId().equals(item.getProductId()))
                .forEach(sale -> quantityByWeekday.merge(
                        sale.salesDate().getDayOfWeek(), sale.quantity(), Long::sum));
        double regularQuantity = 0;
        for (LocalDate day = event.getStartsAt().toLocalDate();
                !day.isAfter(event.getEndsAt().minusNanos(1).toLocalDate()); day = day.plusDays(1)) {
            LocalDateTime from = event.getStartsAt().isAfter(day.atStartOfDay())
                    ? event.getStartsAt() : day.atStartOfDay();
            LocalDateTime to = event.getEndsAt().isBefore(day.plusDays(1).atStartOfDay())
                    ? event.getEndsAt() : day.plusDays(1).atStartOfDay();
            double fraction = Duration.between(from, to).toSeconds() / 86400.0;
            regularQuantity += quantityByWeekday.getOrDefault(day.getDayOfWeek(), 0L) / 4.0 * fraction;
        }
        long actualQuantity = orderItemRepository.sumConfirmedSaleQuantity(
                item.getId(), OrderStatus.CONFIRMED, asOf);
        return new DealDiscountModel.Observation(item.getDiscountRate(), regularQuantity, actualQuantity);
    }
}
