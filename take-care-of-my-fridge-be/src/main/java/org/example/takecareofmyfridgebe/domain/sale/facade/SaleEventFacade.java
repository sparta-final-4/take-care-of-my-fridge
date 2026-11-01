package org.example.takecareofmyfridgebe.domain.sale.facade;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.member.repository.MemberRepository;
import org.example.takecareofmyfridgebe.domain.notification.event.NotificationRequestedMessage;
import org.example.takecareofmyfridgebe.domain.product.dto.ProductDetail;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.service.ProductService;
import org.example.takecareofmyfridgebe.domain.sale.dto.ActiveSale;
import org.example.takecareofmyfridgebe.domain.sale.dto.PopularSaleResponse;
import org.example.takecareofmyfridgebe.domain.sale.dto.SaleEventDetailResponse;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEvent;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventItem;
import org.example.takecareofmyfridgebe.domain.sale.event.FlashDropNotificationEvent;
import org.example.takecareofmyfridgebe.domain.sale.service.FlashDropNotificationService;
import org.example.takecareofmyfridgebe.domain.sale.service.PopularSaleService;
import org.example.takecareofmyfridgebe.domain.sale.service.SaleEventQueryService;
import org.example.takecareofmyfridgebe.domain.search.dto.ProductClickCount;
import org.example.takecareofmyfridgebe.domain.search.service.SearchClickMetricService;
import org.springframework.stereotype.Component;

// 할인 행사 화면·인기 특가·알림에 다른 도메인 값을 붙인다. 행사는 할인 서비스에서, 상품은 상품 서비스에서, 회원은 회원 쪽에서 읽어 합친다.
// 목록·후보 상품 조회와 쓰기는 합칠 것이 없어 컨트롤러가 서비스를 바로 부른다
@Component
@RequiredArgsConstructor
public class SaleEventFacade {

    private final SaleEventQueryService saleEventQueryService;
    private final ProductService productService;
    private final MemberRepository memberRepository;
    private final FlashDropNotificationService flashDropNotificationService;
    private final PopularSaleService popularSaleService;
    private final SearchClickMetricService searchClickMetricService;

    // 메인 화면 인기 특가. 진행 중인 할인, 상품, 최근 검색 클릭을 모아 인기 특가 서비스에 넘기고 특가 카드 모양으로 내려 준다.
    public List<PopularSaleResponse> getPopularSales() {
        Map<Long, ActiveSale> salesByProductId = popularSaleService.findActiveSales();
        List<Product> products = productService.getProductsByIds(List.copyOf(salesByProductId.keySet()));
        LocalDateTime now = LocalDateTime.now(SaleEvent.ZONE);
        LocalDate today = now.toLocalDate();
        Map<Long, Long> clicksByProductId = searchClickMetricService.getProductClicks(popularSaleService.clickFrom(today), today, now)
                .stream()
                .collect(Collectors.toMap(ProductClickCount::productId, ProductClickCount::clicks));

        return popularSaleService.rank(products, salesByProductId, clicksByProductId).stream()
                .map(PopularSaleResponse::from)
                .toList();
    }

    public SaleEventDetailResponse getSaleEvent(Long saleEventId) {
        SaleEvent saleEvent = saleEventQueryService.getSaleEvent(saleEventId);
        List<SaleEventItem> items = saleEventQueryService.getSaleEventItems(saleEventId);
        // 종료된 행사의 상품이 판매 중지됐어도 보여야 해서 상태로 거르지 않는 조회를 쓴다
        Map<Long, Product> productsById = productService
                .getProductDetailsByIds(items.stream().map(SaleEventItem::getProductId).toList()).stream()
                .map(ProductDetail::product)
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        return SaleEventDetailResponse.from(saleEvent, items, productsById);
    }

    // 번개 드롭 등록·시작이 커밋된 뒤 불린다. 끝난 트랜잭션에 섞이지 않게 읽기마다 새로 연다.
    // 받는 사람마다 알림 요청 메시지를 만든다.
    public List<NotificationRequestedMessage> getFlashDropNotifications(FlashDropNotificationEvent event) {
        List<SaleEventItem> items = saleEventQueryService.getItemsWithSaleEvent(event.saleEventIds());
        List<Long> productIds = items.stream().map(SaleEventItem::getProductId).distinct().toList();
        // 판매 중지된 상품도 이름이 필요해 상태로 거르지 않는 조회를 쓴다
        Map<Long, String> productNames = productService.getProductDetailsByIds(productIds).stream()
                .map(ProductDetail::product)
                .collect(Collectors.toMap(Product::getId, Product::getName));
        List<Long> memberIds = memberRepository.findIdsByRoleAndStatus(Member.Role.USER, Member.Status.ACTIVE);
        return flashDropNotificationService.createMessages(event.moment(), items, productNames, memberIds);
    }
}
