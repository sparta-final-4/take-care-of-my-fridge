package org.example.takecareofmyfridgebe.domain.refund.controller;

import org.example.takecareofmyfridgebe.domain.auth.service.AuthSessionService;
import org.example.takecareofmyfridgebe.domain.auth.service.AuthSessionService.AuthPrincipal;
import org.example.takecareofmyfridgebe.domain.auth.service.JwtTokenService;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.order.entity.SaleType;
import org.example.takecareofmyfridgebe.domain.refund.dto.RefundCreateRequest;
import org.example.takecareofmyfridgebe.domain.refund.dto.RefundHistoryItemResponse;
import org.example.takecareofmyfridgebe.domain.refund.dto.RefundHistoryResponse;
import org.example.takecareofmyfridgebe.domain.refund.dto.RefundResponse;
import org.example.takecareofmyfridgebe.domain.refund.dto.RefundSummaryResponse;
import org.example.takecareofmyfridgebe.domain.refund.dto.RefundSummaryStatus;
import org.example.takecareofmyfridgebe.domain.refund.dto.RefundableItemResponse;
import org.example.takecareofmyfridgebe.domain.refund.entity.RefundReasonType;
import org.example.takecareofmyfridgebe.domain.refund.entity.RefundStatus;
import org.example.takecareofmyfridgebe.domain.refund.facade.RefundFacade;
import org.example.takecareofmyfridgebe.domain.refund.service.RefundQueryService;
import org.example.takecareofmyfridgebe.global.config.SecurityConfig;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RefundController의 웹 계층(MockMvc + SecurityConfig) 슬라이스 테스트.
 * 인증 여부, 요청 검증, 토큰의 회원 ID 전달, 비즈니스 예외 응답 변환을 확인한다.
 */
@WebMvcTest(RefundController.class)
@Import({SecurityConfig.class, RefundControllerTest.TestConfig.class})
class RefundControllerTest {

    private static final Long MEMBER_ID = 7L;
    private static final String VALID_ITEMS = "[{\"orderItemId\": 1, \"quantity\": 2}]";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RefundFacade refundFacade;

    @Autowired
    private RefundQueryService refundQueryService;

    @BeforeEach
    void resetMock() {
        reset(refundFacade, refundQueryService);
    }

    /**
     * 비로그인 상태로 환불을 신청하면 Facade를 호출하지 않고 401(A003)을 반환하는지 검증한다.
     */
    @Test
    void requestWithoutLoginReturnsUnauthorized() throws Exception {
        mvc.perform(post("/api/refunds")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody("10", VALID_ITEMS, "\"CHANGE_OF_MIND\"", "null")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.UNAUTHORIZED.getCode()));

        verifyNoInteractions(refundFacade);
    }

    /**
     * 환불 신청 시 토큰의 회원 ID와 요청 본문이 Facade로 전달되고,
     * 201과 함께 응답 DTO가 규격대로 직렬화되는지 검증한다.
     */
    @Test
    void requestPassesTokenMemberIdAndReturnsCreated() throws Exception {
        RefundCreateRequest request = new RefundCreateRequest(
                10L, List.of(new RefundCreateRequest.Item(1L, 2)), RefundReasonType.CHANGE_OF_MIND, "포장이 뜯겨 있어요");
        RefundResponse response = new RefundResponse(
                100L, RefundStatus.COMPLETED, 6000L, LocalDateTime.of(2026, 10, 23, 10, 0));
        when(refundFacade.request(MEMBER_ID, request)).thenReturn(response);

        mvc.perform(post("/api/refunds")
                        .with(csrf())
                        .with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody("10", VALID_ITEMS, "\"CHANGE_OF_MIND\"", "\"포장이 뜯겨 있어요\"")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.refundId").value(100))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.refundAmount").value(6000))
                .andExpect(jsonPath("$.data.refundedAt").value("2026-10-23T10:00:00"));

        verify(refundFacade).request(MEMBER_ID, request);
    }

    /**
     * 주문 ID 누락, 빈 상품 목록, 0개 수량, 사유 누락, 500자를 넘는 상세 사유면
     * Facade를 호출하지 않고 400(C001)을 반환하는지 검증한다.
     */
    @Test
    void requestWithInvalidBodyReturnsBadRequest() throws Exception {
        for (String body : List.of(
                requestBody("null", VALID_ITEMS, "\"CHANGE_OF_MIND\"", "null"),
                requestBody("10", "[]", "\"CHANGE_OF_MIND\"", "null"),
                requestBody("10", "[{\"orderItemId\": 1, \"quantity\": 0}]", "\"CHANGE_OF_MIND\"", "null"),
                requestBody("10", VALID_ITEMS, "null", "null"),
                requestBody("10", VALID_ITEMS, "\"ETC\"", "\"" + "가".repeat(501) + "\""))) {
            mvc.perform(post("/api/refunds")
                            .with(csrf())
                            .with(loginUser())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT_VALUE.getCode()));
        }

        verifyNoInteractions(refundFacade);
    }

    /**
     * 구매 확정된 주문을 환불하려 하면 해당 ErrorCode의 상태와 코드(409, REFUND_010)로 응답하는지 검증한다.
     */
    @Test
    void requestConfirmedOrderReturnsErrorCode() throws Exception {
        when(refundFacade.request(anyLong(), any()))
                .thenThrow(new BusinessException(ErrorCode.REFUND_ORDER_CONFIRMED));

        mvc.perform(post("/api/refunds")
                        .with(csrf())
                        .with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody("10", VALID_ITEMS, "\"CHANGE_OF_MIND\"", "null")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.REFUND_ORDER_CONFIRMED.getCode()));
    }

    /**
     * 비로그인 상태로 환불 가능 수량을 조회하면 서비스를 호출하지 않고 401(A003)을 반환하는지 검증한다.
     */
    @Test
    void getRefundableItemsWithoutLoginReturnsUnauthorized() throws Exception {
        mvc.perform(get("/api/refunds/refundable-items").param("orderId", "10"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.UNAUTHORIZED.getCode()));

        verifyNoInteractions(refundQueryService);
    }

    /**
     * orderId 없이 조회하면 서비스를 호출하지 않고 400(C001)을 반환하는지 검증한다.
     */
    @Test
    void getRefundableItemsWithoutOrderIdReturnsBadRequest() throws Exception {
        mvc.perform(get("/api/refunds/refundable-items").with(loginUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT_VALUE.getCode()));

        verifyNoInteractions(refundQueryService);
    }

    /**
     * 토큰의 회원 ID와 쿼리의 주문 ID로 서비스를 호출하고, 상품별 환불 가능 수량이 규격대로 직렬화되는지 검증한다.
     */
    @Test
    void getRefundableItemsPassesTokenMemberIdAndReturnsItems() throws Exception {
        when(refundQueryService.getRefundableItems(MEMBER_ID, 10L)).thenReturn(List.of(
                new RefundableItemResponse(1L, 100L, "대파", "https://img.test/greenonion.png", 3000,
                        5, 2, 3, SaleType.NONE, true),
                new RefundableItemResponse(2L, 200L, "두부", null, 1000, 1, 0, 0, SaleType.SPECIAL, false)));

        mvc.perform(get("/api/refunds/refundable-items").param("orderId", "10").with(loginUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].orderItemId").value(1))
                .andExpect(jsonPath("$.data[0].productId").value(100))
                .andExpect(jsonPath("$.data[0].productName").value("대파"))
                .andExpect(jsonPath("$.data[0].productImageUrl").value("https://img.test/greenonion.png"))
                .andExpect(jsonPath("$.data[0].unitPrice").value(3000))
                .andExpect(jsonPath("$.data[0].orderedQuantity").value(5))
                .andExpect(jsonPath("$.data[0].refundedQuantity").value(2))
                .andExpect(jsonPath("$.data[0].refundableQuantity").value(3))
                .andExpect(jsonPath("$.data[0].saleType").value("NONE"))
                .andExpect(jsonPath("$.data[0].refundable").value(true))
                .andExpect(jsonPath("$.data[1].saleType").value("SPECIAL"))
                .andExpect(jsonPath("$.data[1].refundable").value(false));

        verify(refundQueryService).getRefundableItems(MEMBER_ID, 10L);
    }

    /**
     * 구매 확정된 주문을 조회하면 409(REFUND_010)로 응답하는지 검증한다.
     */
    @Test
    void getRefundableItemsConfirmedOrderReturnsErrorCode() throws Exception {
        when(refundQueryService.getRefundableItems(MEMBER_ID, 10L))
                .thenThrow(new BusinessException(ErrorCode.REFUND_ORDER_CONFIRMED));

        mvc.perform(get("/api/refunds/refundable-items").param("orderId", "10").with(loginUser()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.REFUND_ORDER_CONFIRMED.getCode()));
    }

    /**
     * 비로그인 상태로 환불 내역을 조회하면 서비스를 호출하지 않고 401(A003)을 반환하는지 검증한다.
     */
    @Test
    void getRefundHistoryWithoutLoginReturnsUnauthorized() throws Exception {
        mvc.perform(get("/api/refunds").param("orderId", "10"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.UNAUTHORIZED.getCode()));

        verifyNoInteractions(refundQueryService);
    }

    /**
     * orderId 없이 환불 내역을 조회하면 서비스를 호출하지 않고 400(C001)을 반환하는지 검증한다.
     */
    @Test
    void getRefundHistoryWithoutOrderIdReturnsBadRequest() throws Exception {
        mvc.perform(get("/api/refunds").with(loginUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT_VALUE.getCode()));

        verifyNoInteractions(refundQueryService);
    }

    /**
     * 토큰의 회원 ID와 쿼리의 주문 ID로 서비스를 호출하고, 환불 내역과 환불 상품이 규격대로 직렬화되는지 검증한다.
     */
    @Test
    void getRefundHistoryPassesTokenMemberIdAndReturnsHistory() throws Exception {
        when(refundQueryService.getRefundHistory(MEMBER_ID, 10L)).thenReturn(List.of(
                new RefundHistoryResponse(100L, RefundStatus.COMPLETED, RefundReasonType.CHANGE_OF_MIND,
                        "포장이 뜯겨 있어요", 6000L, LocalDateTime.of(2026, 10, 23, 9, 59),
                        LocalDateTime.of(2026, 10, 23, 10, 0),
                        List.of(new RefundHistoryItemResponse(1L, "대파", "https://img.test/greenonion.png",
                                3000, 2, 6000L)))));

        mvc.perform(get("/api/refunds").param("orderId", "10").with(loginUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].refundId").value(100))
                .andExpect(jsonPath("$.data[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data[0].reasonType").value("CHANGE_OF_MIND"))
                .andExpect(jsonPath("$.data[0].reasonDetail").value("포장이 뜯겨 있어요"))
                .andExpect(jsonPath("$.data[0].refundAmount").value(6000))
                .andExpect(jsonPath("$.data[0].requestedAt").value("2026-10-23T09:59:00"))
                .andExpect(jsonPath("$.data[0].refundedAt").value("2026-10-23T10:00:00"))
                .andExpect(jsonPath("$.data[0].items[0].orderItemId").value(1))
                .andExpect(jsonPath("$.data[0].items[0].productName").value("대파"))
                .andExpect(jsonPath("$.data[0].items[0].productImageUrl").value("https://img.test/greenonion.png"))
                .andExpect(jsonPath("$.data[0].items[0].unitPrice").value(3000))
                .andExpect(jsonPath("$.data[0].items[0].quantity").value(2))
                .andExpect(jsonPath("$.data[0].items[0].amount").value(6000));

        verify(refundQueryService).getRefundHistory(MEMBER_ID, 10L);
    }

    /**
     * 비로그인 상태로 환불 요약을 조회하면 서비스를 호출하지 않고 401(A003)을 반환하는지 검증한다.
     */
    @Test
    void getRefundSummariesWithoutLoginReturnsUnauthorized() throws Exception {
        mvc.perform(get("/api/refunds/summaries").param("orderIds", "10"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(refundQueryService);
    }

    /**
     * 쉼표로 받은 주문 ID 목록과 토큰의 회원 ID로 서비스를 호출하고, 주문별 환불 요약이 규격대로 직렬화되는지 검증한다.
     */
    @Test
    void getRefundSummariesPassesOrderIdsAndReturnsSummaries() throws Exception {
        when(refundQueryService.getRefundSummaries(MEMBER_ID, List.of(10L, 11L))).thenReturn(List.of(
                new RefundSummaryResponse(10L, RefundSummaryStatus.PARTIAL, 3000L),
                new RefundSummaryResponse(11L, RefundSummaryStatus.NONE, 0L)));

        mvc.perform(get("/api/refunds/summaries").param("orderIds", "10,11").with(loginUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].orderId").value(10))
                .andExpect(jsonPath("$.data[0].refundStatus").value("PARTIAL"))
                .andExpect(jsonPath("$.data[0].refundedAmount").value(3000))
                .andExpect(jsonPath("$.data[1].refundStatus").value("NONE"));

        verify(refundQueryService).getRefundSummaries(MEMBER_ID, List.of(10L, 11L));
    }

    /**
     * 빈 값이 섞인 주문 ID 목록(orderIds=10,,11)도 500 없이 서비스로 넘어가는지 검증한다. 빈 값은 서비스가 걸러낸다.
     */
    @Test
    void getRefundSummariesWithEmptyElementPassesToService() throws Exception {
        when(refundQueryService.getRefundSummaries(eq(MEMBER_ID), any())).thenReturn(List.of());

        mvc.perform(get("/api/refunds/summaries").param("orderIds", "10,,11").with(loginUser()))
                .andExpect(status().isOk());

        verify(refundQueryService).getRefundSummaries(eq(MEMBER_ID), argThat(ids -> ids.containsAll(List.of(10L, 11L))));
    }

    /**
     * 서비스가 REFUND_013으로 거절하면 400과 해당 오류 코드로 응답하는지 검증한다. 1~50건 범위 판단은 서비스 테스트가 맡는다.
     */
    @Test
    void getRefundSummariesBusinessExceptionReturnsErrorCode() throws Exception {
        when(refundQueryService.getRefundSummaries(MEMBER_ID, List.of(10L)))
                .thenThrow(new BusinessException(ErrorCode.INVALID_REFUND_SUMMARY_REQUEST));

        mvc.perform(get("/api/refunds/summaries").param("orderIds", "10").with(loginUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_REFUND_SUMMARY_REQUEST.getCode()));
    }

    private static RequestPostProcessor loginUser() {
        return authentication(new UsernamePasswordAuthenticationToken(
                new AuthPrincipal(MEMBER_ID, 8L, Member.Role.USER), null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private static String requestBody(String orderId, String items, String reasonType, String reasonDetail) {
        return """
                {
                  "orderId": %s,
                  "items": %s,
                  "reasonType": %s,
                  "reasonDetail": %s
                }
                """.formatted(orderId, items, reasonType, reasonDetail);
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        RefundFacade refundFacade() {
            return mock(RefundFacade.class);
        }

        @Bean
        RefundQueryService refundQueryService() {
            return mock(RefundQueryService.class);
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
