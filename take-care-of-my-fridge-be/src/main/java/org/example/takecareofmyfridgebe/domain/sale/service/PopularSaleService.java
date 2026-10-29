package org.example.takecareofmyfridgebe.domain.sale.service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.sale.dto.ActiveSale;
import org.example.takecareofmyfridgebe.domain.sale.dto.PopularSaleProduct;
import org.example.takecareofmyfridgebe.domain.sale.dto.SellingPrice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 메인 화면에 보여 줄 인기 특가를 고른다. 진행 중인 특가·번개 드롭 중 지금 살 수 있는 상품을 최근 검색 클릭이 많은 순으로 둔다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PopularSaleService {

    private static final int LIMIT = 6;
    private static final int CLICK_DAYS = 7;

    private final SaleEventReader saleEventReader;

    // 인기 특가 후보. 지금 진행 중인 특가·번개 드롭 전부를 상품 ID별로 돌려준다.
    public Map<Long, ActiveSale> findActiveSales() {
        return saleEventReader.findAllActiveSales();
    }

    // 클릭을 세기 시작하는 날. 오늘을 포함해 최근 7일이다.
    public LocalDate clickFrom(LocalDate today) {
        return today.minusDays(CLICK_DAYS - 1);
    }

    // 품절(번개 드롭 소진 포함)을 빼고 클릭이 많은 순, 같으면 할인율이 높은 순, 그래도 같으면 상품 ID 순으로 최대 6개.
    // salesByProductId에 없는 상품은 들어오지 않는다고 본다.
    public List<PopularSaleProduct> rank(List<Product> products, Map<Long, ActiveSale> salesByProductId, Map<Long, Long> clicksByProductId) {
        Comparator<PopularSaleProduct> popularFirst = Comparator
                .comparingLong((PopularSaleProduct candidate) -> clicksByProductId.getOrDefault(candidate.product().getId(), 0L))
                .thenComparingInt(candidate -> candidate.sale().discountRate())
                .reversed()
                .thenComparing(candidate -> candidate.product().getId());

        return products.stream()
                .map(product -> new PopularSaleProduct(product, salesByProductId.get(product.getId())))
                .filter(candidate -> !SellingPrice.of(candidate.product(), candidate.sale()).soldOut())
                .sorted(popularFirst)
                .limit(LIMIT)
                .toList();
    }
}
