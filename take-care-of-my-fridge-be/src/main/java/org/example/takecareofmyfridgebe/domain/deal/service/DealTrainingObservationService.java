package org.example.takecareofmyfridgebe.domain.deal.service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.order.dto.DailyProductSales;
import org.example.takecareofmyfridgebe.domain.order.dto.SaleEventItemQuantity;
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
        List<SaleEventItem> items = saleEventItemRepository.findFinishedForTraining(
                SaleEventType.SPECIAL, asOf.minusDays(7));
        if (items.isEmpty()) {
            return List.of();
        }
        // 행사 상품마다 쿼리를 보내지 않도록 주문 집계를 한 번에 읽는다.
        List<Long> itemIds = items.stream().map(SaleEventItem::getId).toList();
        Set<Long> unfinishedItemIds = new HashSet<>(orderItemRepository
                .findSaleEventItemIdsWithUnconfirmedPaidOrders(itemIds, OrderStatus.PAID, asOf));
        Map<Long, Long> soldQuantities = orderItemRepository
                .sumConfirmedSaleQuantities(itemIds, OrderStatus.CONFIRMED, asOf).stream()
                .collect(Collectors.toMap(SaleEventItemQuantity::saleEventItemId, SaleEventItemQuantity::quantity));
        Map<Long, List<DailyProductSales>> regularSalesByEvent = new HashMap<>();
        return items.stream()
                .filter(item -> !unfinishedItemIds.contains(item.getId()))
                .map(item -> observation(item, asOf, regularSalesByEvent,
                        soldQuantities.getOrDefault(item.getId(), 0L)))
                .filter(observation -> observation.expectedRegularQuantity() > 0)
                .toList();
    }

    private DealDiscountModel.Observation observation(SaleEventItem item, LocalDateTime asOf,
            Map<Long, List<DailyProductSales>> regularSalesByEvent, long actualQuantity) {
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
        // 중간에 닫은 행사는 닫은 시각까지만 판매했으므로, 정상 판매 기대량도 그 시각까지만 센다.
        LocalDateTime endsAt = event.actualEndsAt();
        double regularQuantity = 0;
        for (LocalDate day = event.getStartsAt().toLocalDate();
                !day.isAfter(endsAt.minusNanos(1).toLocalDate()); day = day.plusDays(1)) {
            LocalDateTime from = event.getStartsAt().isAfter(day.atStartOfDay())
                    ? event.getStartsAt() : day.atStartOfDay();
            LocalDateTime to = endsAt.isBefore(day.plusDays(1).atStartOfDay())
                    ? endsAt : day.plusDays(1).atStartOfDay();
            double fraction = Duration.between(from, to).toSeconds() / 86400.0;
            regularQuantity += quantityByWeekday.getOrDefault(day.getDayOfWeek(), 0L) / 4.0 * fraction;
        }
        return new DealDiscountModel.Observation(item.getDiscountRate(), regularQuantity, actualQuantity);
    }
}
