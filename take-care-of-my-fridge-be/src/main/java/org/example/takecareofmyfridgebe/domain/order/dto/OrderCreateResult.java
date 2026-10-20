package org.example.takecareofmyfridgebe.domain.order.dto;

// 새 주문을 만들었는지(201)와 같은 멱등 키로 이미 만든 주문을 돌려주는지(200)를 컨트롤러가 가르도록 함께 돌려준다
public record OrderCreateResult(OrderDetailResponse response, boolean created) {
}
