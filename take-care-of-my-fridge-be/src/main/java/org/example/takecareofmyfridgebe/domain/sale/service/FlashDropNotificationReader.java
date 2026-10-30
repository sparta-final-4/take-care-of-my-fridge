package org.example.takecareofmyfridgebe.domain.sale.service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.member.repository.MemberRepository;
import org.example.takecareofmyfridgebe.domain.notification.entity.NotificationType;
import org.example.takecareofmyfridgebe.domain.notification.event.NotificationRequestedMessage;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.repository.ProductRepository;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventItem;
import org.example.takecareofmyfridgebe.domain.sale.event.FlashDropNotificationEvent;
import org.example.takecareofmyfridgebe.domain.sale.event.FlashDropNotificationEvent.Moment;
import org.example.takecareofmyfridgebe.domain.sale.repository.SaleEventItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 번개 드롭 알림을 받을 사람과 문구를 정한다. 받는 사람은 정지되지 않은 일반 회원 전체다.
// 행사 트랜잭션이 커밋된 뒤 불리므로 끝난 트랜잭션에 섞이지 않게 새 읽기 트랜잭션에서 읽는다
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
public class FlashDropNotificationReader {

    private static final DateTimeFormatter STARTS_AT_FORMAT = DateTimeFormatter.ofPattern("M월 d일 HH:mm");

    private final SaleEventItemRepository saleEventItemRepository;
    private final ProductRepository productRepository;
    private final MemberRepository memberRepository;

    public List<NotificationRequestedMessage> read(FlashDropNotificationEvent event) {
        List<SaleEventItem> items = saleEventItemRepository.findAllWithSaleEventBySaleEventIdIn(event.saleEventIds());
        Set<Long> productIds = items.stream()
                .map(SaleEventItem::getProductId)
                .collect(Collectors.toSet());
        Map<Long, String> productNames = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Product::getName));
        List<Long> memberIds = memberRepository.findIdsByRoleAndStatus(Member.Role.USER, Member.Status.ACTIVE);

        return items.stream()
                .flatMap(item -> memberIds.stream()
                        .map(memberId -> toMessage(event.moment(), item, productNames.get(item.getProductId()), memberId)))
                .toList();
    }

    // 같은 행사·상품의 같은 시점 알림은 한 회원에게 한 번만 남도록 중복 방지 키를 붙인다
    private NotificationRequestedMessage toMessage(Moment moment, SaleEventItem item, String productName, Long memberId) {
        String content = switch (moment) {
            case CREATED -> "%s %d%% 번개 드롭 · %s 시작 · %d개 한정".formatted(
                    productName, item.getDiscountRate(),
                    item.getSaleEvent().getStartsAt().format(STARTS_AT_FORMAT), item.getLimitedQuantity());
            case STARTED -> "%s %d%% 번개 드롭 시작 · %d개 한정".formatted(
                    productName, item.getDiscountRate(), item.getLimitedQuantity());
        };
        String dedupeKey = "FLASH_DROP_%s:%d:%d".formatted(moment, item.getSaleEvent().getId(), item.getProductId());
        return new NotificationRequestedMessage(
                memberId, NotificationType.FLASH_DROP, content, "/products/" + item.getProductId(), dedupeKey);
    }
}
