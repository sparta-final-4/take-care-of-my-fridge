package org.example.takecareofmyfridgebe.domain.sale.facade;

import java.util.Collection;
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
import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;
import org.example.takecareofmyfridgebe.domain.product.service.ProductService;
import org.example.takecareofmyfridgebe.domain.sale.dto.FlashDropCreateRequest;
import org.example.takecareofmyfridgebe.domain.sale.dto.SaleEventDetailResponse;
import org.example.takecareofmyfridgebe.domain.sale.dto.SaleEventItemUpdateRequest;
import org.example.takecareofmyfridgebe.domain.sale.dto.SaleEventUpdateRequest;
import org.example.takecareofmyfridgebe.domain.sale.dto.SaleEventsCreateResponse;
import org.example.takecareofmyfridgebe.domain.sale.dto.SpecialSaleCreateRequest;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEvent;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventItem;
import org.example.takecareofmyfridgebe.domain.sale.event.FlashDropNotificationEvent;
import org.example.takecareofmyfridgebe.domain.sale.service.FlashDropNotificationService;
import org.example.takecareofmyfridgebe.domain.sale.service.SaleEventQueryService;
import org.example.takecareofmyfridgebe.domain.sale.service.SaleEventService;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.stereotype.Component;

// 관리자 행사 화면과 번개 드롭 알림에 다른 도메인 값을 붙인다. 행사는 할인 서비스에서, 상품은 상품 서비스에서 읽는다.
// 등록·수정은 상품을 여기서 읽어 SaleEventService에 넘긴다. 등록은 없는 상품과 판매 중이 아닌 상품을 여기서 거른다.
// 상품 행을 잠그지 않으므로 상품 읽기는 쓰기 트랜잭션 밖에서 한다.
// 알림 받을 회원 ID는 MemberService에 맞는 메서드가 없어 MemberRepository를 직접 쓴다.
// 목록·후보 상품 조회와 종료·삭제는 합칠 것이 없어 컨트롤러가 서비스를 바로 부른다.
@Component
@RequiredArgsConstructor
public class AdminSaleEventFacade {

    private final SaleEventQueryService saleEventQueryService;
    private final SaleEventService saleEventService;
    private final ProductService productService;
    private final MemberRepository memberRepository;
    private final FlashDropNotificationService flashDropNotificationService;

    public SaleEventDetailResponse getSaleEvent(Long saleEventId) {
        SaleEvent saleEvent = saleEventQueryService.getSaleEvent(saleEventId);
        List<SaleEventItem> items = saleEventQueryService.getSaleEventItems(saleEventId);
        // 종료된 행사의 상품이 판매 중지됐어도 보여야 해서 상태로 거르지 않는 조회를 쓴다.
        Map<Long, Product> productsById = findProducts(items.stream().map(SaleEventItem::getProductId).toList()).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        return SaleEventDetailResponse.from(saleEvent, items, productsById);
    }

    // 번개 드롭 등록·시작이 커밋된 뒤 불린다. 끝난 트랜잭션에 섞이지 않게 읽기마다 새로 연다.
    // 받는 사람마다 알림 요청 메시지를 만든다.
    public List<NotificationRequestedMessage> getFlashDropNotifications(FlashDropNotificationEvent event) {
        List<SaleEventItem> items = saleEventQueryService.getItemsWithSaleEvent(event.saleEventIds());
        List<Long> productIds = items.stream().map(SaleEventItem::getProductId).distinct().toList();
        // 판매 중지된 상품도 이름이 필요해 상태로 거르지 않는 조회를 쓴다.
        Map<Long, String> productNames = findProducts(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Product::getName));
        List<Long> memberIds = memberRepository.findIdsByRoleAndStatus(Member.Role.USER, Member.Status.ACTIVE);
        return flashDropNotificationService.createMessages(event.moment(), items, productNames, memberIds);
    }

    public SaleEventsCreateResponse createSpecialSale(SpecialSaleCreateRequest request) {
        List<Product> products = findProducts(request.productIds());
        validateAvailable(request.productIds(), products);
        return saleEventService.createSpecialSale(request, products);
    }

    public SaleEventsCreateResponse createFlashDrop(FlashDropCreateRequest request) {
        List<Product> products = findProducts(request.productIds());
        validateAvailable(request.productIds(), products);
        return saleEventService.createFlashDrop(request, products);
    }

    public void update(Long saleEventId, SaleEventUpdateRequest request) {
        List<Long> productIds = request.items().stream().map(SaleEventItemUpdateRequest::productId).toList();
        saleEventService.update(saleEventId, request, findProducts(productIds));
    }

    // 상태로 거르지 않고 읽는다. 판매 중지 상품도 이름을 보이거나 상태를 보고 거절해야 한다.
    private List<Product> findProducts(Collection<Long> productIds) {
        return productService.getProductDetailsByIds(productIds).stream()
                .map(ProductDetail::product)
                .toList();
    }

    // 없는 상품과 판매 중이 아닌 상품은 행사에 넣을 수 없다.
    private void validateAvailable(List<Long> productIds, List<Product> products) {
        if (products.size() != productIds.size()) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        products.stream()
                .filter(product -> product.getStatus() != ProductStatus.ON_SALE)
                .findFirst()
                .ifPresent(product -> {
                    throw new BusinessException(ErrorCode.PRODUCT_NOT_ON_SALE, toNotOnSaleMessage(product));
                });
    }

    private String toNotOnSaleMessage(Product product) {
        String reason = product.getStatus() == ProductStatus.SOLD_OUT ? "품절" : "판매 중지";
        return reason + " · " + product.getName();
    }
}
