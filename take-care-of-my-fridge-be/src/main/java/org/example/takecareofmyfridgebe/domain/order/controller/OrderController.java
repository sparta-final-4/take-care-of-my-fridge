package org.example.takecareofmyfridgebe.domain.order.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.auth.service.AuthSessionService;
import org.example.takecareofmyfridgebe.domain.order.dto.OrderCancelResponse;
import org.example.takecareofmyfridgebe.domain.order.dto.OrderCheckoutResponse;
import org.example.takecareofmyfridgebe.domain.order.dto.OrderConfirmResponse;
import org.example.takecareofmyfridgebe.domain.order.dto.OrderCreateRequest;
import org.example.takecareofmyfridgebe.domain.order.dto.OrderCreateResult;
import org.example.takecareofmyfridgebe.domain.order.dto.OrderDetailResponse;
import org.example.takecareofmyfridgebe.domain.order.dto.OrderListRequest;
import org.example.takecareofmyfridgebe.domain.order.dto.OrderSummaryResponse;
import org.example.takecareofmyfridgebe.domain.order.service.OrderCancelService;
import org.example.takecareofmyfridgebe.domain.order.service.OrderConfirmService;
import org.example.takecareofmyfridgebe.domain.order.service.OrderCreateService;
import org.example.takecareofmyfridgebe.domain.order.service.OrderQueryService;
import org.example.takecareofmyfridgebe.global.response.ApiResponse;
import org.example.takecareofmyfridgebe.global.response.PageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderCreateService orderCreateService;
    private final OrderQueryService orderQueryService;
    private final OrderCancelService orderCancelService;
    private final OrderConfirmService orderConfirmService;

    @PostMapping
    public ResponseEntity<ApiResponse<OrderCheckoutResponse>> createOrder(
            @AuthenticationPrincipal AuthSessionService.AuthPrincipal principal,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody OrderCreateRequest request) {
        OrderCreateResult result = orderCreateService.createOrder(principal.memberId(), idempotencyKey, request);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.ok(result.response()));
    }

    // 내 주문 내역 화면
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<OrderSummaryResponse>>> getMyOrders(
            @AuthenticationPrincipal AuthSessionService.AuthPrincipal principal,
            @Valid @ModelAttribute OrderListRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(orderQueryService.getMyOrders(principal.memberId(), request)));
    }

    // 주문 상세 화면
    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderDetailResponse>> getDetail(
            @AuthenticationPrincipal AuthSessionService.AuthPrincipal principal,
            @PathVariable Long orderId) {
        return ResponseEntity.ok(ApiResponse.ok(orderQueryService.getDetail(principal.memberId(), orderId)));
    }

    // 주문 확인 화면(결제 대기 주문)
    @GetMapping("/{orderId}/checkout")
    public ResponseEntity<ApiResponse<OrderCheckoutResponse>> getCheckout(
            @AuthenticationPrincipal AuthSessionService.AuthPrincipal principal,
            @PathVariable Long orderId) {
        return ResponseEntity.ok(ApiResponse.ok(orderQueryService.getCheckout(principal.memberId(), orderId)));
    }

    // 주문 확인 화면의 취소하기. 이미 취소된 주문에도 같은 200 응답을 준다.
    @PatchMapping("/{orderId}/cancel")
    public ResponseEntity<ApiResponse<OrderCancelResponse>> cancelOrder(
            @AuthenticationPrincipal AuthSessionService.AuthPrincipal principal,
            @PathVariable Long orderId) {
        return ResponseEntity.ok(ApiResponse.ok(orderCancelService.cancel(principal.memberId(), orderId)));
    }

    // 주문 상세 화면의 구매 확정. 이미 구매 확정된 주문에도 같은 200 응답을 준다.
    @PatchMapping("/{orderId}/confirm")
    public ResponseEntity<ApiResponse<OrderConfirmResponse>> confirmOrder(
            @AuthenticationPrincipal AuthSessionService.AuthPrincipal principal,
            @PathVariable Long orderId) {
        return ResponseEntity.ok(ApiResponse.ok(orderConfirmService.confirm(principal.memberId(), orderId)));
    }
}
