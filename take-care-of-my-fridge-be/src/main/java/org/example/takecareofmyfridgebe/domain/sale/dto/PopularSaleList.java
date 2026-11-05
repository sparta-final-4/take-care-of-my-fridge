package org.example.takecareofmyfridgebe.domain.sale.dto;

import java.util.List;

// 메인 화면 특가 카드 목록. 캐시에서 꺼낼 때 목록을 그대로 두면 역직렬화에 실패하므로 record로 감싼다.
public record PopularSaleList(List<PopularSaleResponse> sales) {
}
