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
import org.example.takecareofmyfridgebe.domain.order.dto.OrderCreateResult;
import org.example.takecareofmyfridgebe.domain.order.dto.OrderDetailResponse;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderStatus;
import org.example.takecareofmyfridgebe.domain.order.service.OrderCreateService;
import org.example.takecareofmyfridgebe.global.config.SecurityConfig;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
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

    @Autowired
    OrderControllerTest(MockMvc mvc, OrderCreateService orderCreateService) {
        this.mvc = mvc;
        this.orderCreateService = orderCreateService;
    }

    @BeforeEach
    void resetMock() {
        reset(orderCreateService);
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

    private OrderDetailResponse response() {
        return new OrderDetailResponse(5001L, "20261020143005123001", OrderStatus.PENDING,
                LocalDateTime.of(2026, 10, 20, 14, 30), 3000L, 0L, 3000L, List.of());
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
        JwtTokenService jwtTokenService() {
            return mock(JwtTokenService.class);
        }

        @Bean
        AuthSessionService authSessionService() {
            return mock(AuthSessionService.class);
        }
    }
}
