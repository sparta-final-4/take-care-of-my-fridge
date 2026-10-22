package org.example.takecareofmyfridgebe.domain.order.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.example.takecareofmyfridgebe.domain.auth.service.AuthSessionService;
import org.example.takecareofmyfridgebe.domain.auth.service.JwtTokenService;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.order.dto.OrderCancelResponse;
import org.example.takecareofmyfridgebe.domain.order.dto.OrderCheckoutResponse;
import org.example.takecareofmyfridgebe.domain.order.dto.OrderConfirmResponse;
import org.example.takecareofmyfridgebe.domain.order.dto.OrderCreateResult;
import org.example.takecareofmyfridgebe.domain.order.dto.OrderDetailResponse;
import org.example.takecareofmyfridgebe.domain.order.dto.OrderListRequest;
import org.example.takecareofmyfridgebe.domain.order.dto.OrderSummaryResponse;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderStatus;
import org.example.takecareofmyfridgebe.domain.order.service.OrderCancelService;
import org.example.takecareofmyfridgebe.domain.order.service.OrderConfirmService;
import org.example.takecareofmyfridgebe.domain.order.service.OrderCreateService;
import org.example.takecareofmyfridgebe.domain.order.service.OrderQueryService;
import org.example.takecareofmyfridgebe.global.config.SecurityConfig;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.example.takecareofmyfridgebe.global.response.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(OrderController.class)
@Import({SecurityConfig.class, OrderControllerTest.TestConfig.class})
class OrderControllerTest {

    private static final String BODY = "{\"cartItemIds\": [11]}";

    private final MockMvc mvc;
    private final OrderCreateService orderCreateService;
    private final OrderQueryService orderQueryService;
    private final OrderCancelService orderCancelService;
    private final OrderConfirmService orderConfirmService;

    @Autowired
    OrderControllerTest(MockMvc mvc, OrderCreateService orderCreateService, OrderQueryService orderQueryService,
                        OrderCancelService orderCancelService, OrderConfirmService orderConfirmService) {
        this.mvc = mvc;
        this.orderCreateService = orderCreateService;
        this.orderQueryService = orderQueryService;
        this.orderCancelService = orderCancelService;
        this.orderConfirmService = orderConfirmService;
    }

    @BeforeEach
    void resetMock() {
        reset(orderCreateService, orderQueryService, orderCancelService, orderConfirmService);
    }

    @Test
    @DisplayName("주문 취소는 로그인한 회원 번호와 주문 번호로 서비스를 부르고 200이다")
    void cancelOrder_returns200() throws Exception {
        when(orderCancelService.cancel(7L, 5001L)).thenReturn(new OrderCancelResponse(5001L, OrderStatus.CANCELLED));

        mvc.perform(patch("/api/orders/5001/cancel")
                        .with(csrf())
                        .with(loginAsMember7()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(5001))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("결제가 끝난 주문을 취소하면 409다")
    void cancelOrder_paidOrder_returns409() throws Exception {
        when(orderCancelService.cancel(7L, 5001L)).thenThrow(new BusinessException(ErrorCode.ORDER_ALREADY_PAID));

        mvc.perform(patch("/api/orders/5001/cancel")
                        .with(csrf())
                        .with(loginAsMember7()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ORDER_007"));
    }

    @Test
    @DisplayName("구매 확정은 로그인한 회원 번호와 주문 번호로 서비스를 부르고 200이다")
    void confirmOrder_returns200() throws Exception {
        when(orderConfirmService.confirm(7L, 5001L)).thenReturn(new OrderConfirmResponse(5001L, OrderStatus.CONFIRMED));

        mvc.perform(patch("/api/orders/5001/confirm")
                        .with(csrf())
                        .with(loginAsMember7()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(5001))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
    }

    @Test
    @DisplayName("결제 대기 주문을 구매 확정하면 409다")
    void confirmOrder_pendingOrder_returns409() throws Exception {
        when(orderConfirmService.confirm(7L, 5001L)).thenThrow(new BusinessException(ErrorCode.ORDER_NOT_PAID));

        mvc.perform(patch("/api/orders/5001/confirm")
                        .with(csrf())
                        .with(loginAsMember7()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ORDER_009"));
    }

    @Test
    @DisplayName("구매 확정에서 남의 주문이거나 없는 주문이면 404다")
    void confirmOrder_notOwned_returns404() throws Exception {
        when(orderConfirmService.confirm(7L, 5001L)).thenThrow(new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        mvc.perform(patch("/api/orders/5001/confirm")
                        .with(csrf())
                        .with(loginAsMember7()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ORDER_001"));
    }

    @Test
    @DisplayName("주문 상세 조회는 로그인한 회원 번호와 주문 번호로 서비스를 부르고 200이다")
    void getDetail_returns200() throws Exception {
        when(orderQueryService.getDetail(7L, 5001L)).thenReturn(detailResponse());

        mvc.perform(get("/api/orders/5001")
                        .with(loginAsMember7()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(5001))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.confirmedAt").value("2026-10-21T14:30:00"));
    }

    @Test
    @DisplayName("주문 상세 조회에서 남의 주문이거나 없는 주문이면 404다")
    void getDetail_notOwned_returns404() throws Exception {
        when(orderQueryService.getDetail(7L, 5001L)).thenThrow(new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        mvc.perform(get("/api/orders/5001")
                        .with(loginAsMember7()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ORDER_001"));
    }

    @Test
    @DisplayName("주문 확인 조회는 로그인한 회원 번호와 주문 번호로 서비스를 부르고 200이다")
    void getCheckout_returns200() throws Exception {
        when(orderQueryService.getCheckout(7L, 5001L)).thenReturn(response());

        mvc.perform(get("/api/orders/5001/checkout")
                        .with(loginAsMember7()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(5001))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    @DisplayName("남의 주문이거나 없는 주문이면 404다")
    void getCheckout_notOwned_returns404() throws Exception {
        when(orderQueryService.getCheckout(7L, 5001L)).thenThrow(new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        mvc.perform(get("/api/orders/5001/checkout")
                        .with(loginAsMember7()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ORDER_001"));
    }

    @Test
    @DisplayName("주문 목록은 페이지를 주지 않으면 1페이지 10개로 서비스를 부르고 200이다")
    void getMyOrders_defaultPage_returns200() throws Exception {
        PageResponse<OrderSummaryResponse> page = new PageResponse<>(
                List.of(new OrderSummaryResponse(5001L, "20261020143005123001",
                        LocalDateTime.of(2026, 10, 20, 14, 30), 3000L, OrderStatus.PAID)),
                1, 10, 1L, 1, true);
        when(orderQueryService.getMyOrders(7L, new OrderListRequest(1, 10))).thenReturn(page);

        mvc.perform(get("/api/orders")
                        .with(loginAsMember7()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].orderId").value(5001))
                .andExpect(jsonPath("$.data.content[0].status").value("PAID"))
                .andExpect(jsonPath("$.data.pageNumber").value(1));
    }

    @Test
    @DisplayName("주문 목록의 페이지 크기가 100을 넘으면 400이고 서비스를 부르지 않는다")
    void getMyOrders_tooLargeSize_returns400() throws Exception {
        mvc.perform(get("/api/orders")
                        .param("size", "101")
                        .with(loginAsMember7()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("C001"));

        verifyNoInteractions(orderQueryService);
    }

    @Test
    @DisplayName("새 주문이 만들어지면 201이다")
    void createOrder_created_returns201() throws Exception {
        when(orderCreateService.createOrder(eq(7L), eq("key-1"), any())).thenReturn(new OrderCreateResult(response(), true));

        mvc.perform(post("/api/orders")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY)
                        .with(csrf())
                        .with(loginAsMember7()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.orderId").value(5001));
    }

    @Test
    @DisplayName("같은 멱등 키로 이미 만든 주문을 돌려주면 200이다")
    void createOrder_existing_returns200() throws Exception {
        when(orderCreateService.createOrder(eq(7L), eq("key-1"), any())).thenReturn(new OrderCreateResult(response(), false));

        mvc.perform(post("/api/orders")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY)
                        .with(csrf())
                        .with(loginAsMember7()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(5001));
    }

    @Test
    @DisplayName("멱등 키 헤더가 없으면 서비스의 거절이 400으로 나간다")
    void createOrder_missingKey_returns400() throws Exception {
        when(orderCreateService.createOrder(eq(7L), isNull(), any()))
                .thenThrow(new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "Idempotency-Key 헤더가 필요합니다. (64자 이하)"));

        mvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY)
                        .with(csrf())
                        .with(loginAsMember7()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("C001"));
    }

    @Test
    @DisplayName("장바구니 항목 번호가 비어 있으면 400이고 서비스를 부르지 않는다")
    void createOrder_emptyCartItemIds_returns400() throws Exception {
        mvc.perform(post("/api/orders")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cartItemIds\": []}")
                        .with(csrf())
                        .with(loginAsMember7()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("C001"));

        verifyNoInteractions(orderCreateService);
    }

    @Test
    @DisplayName("장바구니 항목 번호가 50개를 넘으면 400이고 서비스를 부르지 않는다")
    void createOrder_tooManyCartItemIds_returns400() throws Exception {
        String ids = IntStream.rangeClosed(1, 51).mapToObj(String::valueOf).collect(Collectors.joining(","));

        mvc.perform(post("/api/orders")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cartItemIds\": [" + ids + "]}")
                        .with(csrf())
                        .with(loginAsMember7()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("C001"));

        verifyNoInteractions(orderCreateService);
    }

    private OrderCheckoutResponse response() {
        return new OrderCheckoutResponse(5001L, "20261020143005123001", OrderStatus.PENDING,
                LocalDateTime.of(2026, 10, 20, 14, 30), 3000L, 0L, 3000L, List.of());
    }

    private OrderDetailResponse detailResponse() {
        return new OrderDetailResponse(5001L, "20261020143005123001", OrderStatus.CONFIRMED,
                LocalDateTime.of(2026, 10, 20, 14, 30), LocalDateTime.of(2026, 10, 21, 14, 30),
                3000L, 0L, 3000L, List.of());
    }

    private RequestPostProcessor loginAsMember7() {
        return authentication(new UsernamePasswordAuthenticationToken(
                new AuthSessionService.AuthPrincipal(7L, 8L, Member.Role.USER), null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        OrderCreateService orderCreateService() {
            return mock(OrderCreateService.class);
        }

        @Bean
        OrderQueryService orderQueryService() {
            return mock(OrderQueryService.class);
        }

        @Bean
        OrderCancelService orderCancelService() {
            return mock(OrderCancelService.class);
        }

        @Bean
        OrderConfirmService orderConfirmService() {
            return mock(OrderConfirmService.class);
        }

        @Bean
        JwtTokenService jwtTokenService() {
            return mock(JwtTokenService.class);
        }

        @Bean
        AuthSessionService authSessionService() {
            return mock(AuthSessionService.class);
        }
    }
}
