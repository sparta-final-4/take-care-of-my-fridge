package org.example.takecareofmyfridgebe.domain.payment.facade;

import org.example.takecareofmyfridgebe.domain.payment.dto.BillingKeyPrepareResponse;
import org.example.takecareofmyfridgebe.domain.payment.dto.BillingKeyRegisterRequest;
import org.example.takecareofmyfridgebe.domain.payment.dto.BillingKeyResponse;
import org.example.takecareofmyfridgebe.domain.payment.entity.CardType;
import org.example.takecareofmyfridgebe.domain.payment.port.BillingKeyGateway;
import org.example.takecareofmyfridgebe.domain.payment.port.BillingKeyInfo;
import org.example.takecareofmyfridgebe.domain.payment.service.BillingKeyService;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class BillingKeyFacadeTest {

    private static final Long MEMBER_ID = 7L;
    private static final String BILLING_KEY = "billing-key-1";
    private static final BillingKeyRegisterRequest REQUEST = new BillingKeyRegisterRequest(BILLING_KEY);

    private BillingKeyService billingKeyService;
    private BillingKeyGateway billingKeyGateway;
    private BillingKeyFacade billingKeyFacade;

    @BeforeEach
    void setUp() {
        billingKeyService = mock(BillingKeyService.class);
        billingKeyGateway = mock(BillingKeyGateway.class);
        billingKeyFacade = new BillingKeyFacade(billingKeyService, billingKeyGateway);
    }

    @Test
    @DisplayName("등록 준비를 하면 정기결제 채널 키, 40자 발급 ID, 창 제목, 회원 고객 ID를 돌려준다")
    void prepare_success() {
        given(billingKeyGateway.billingChannelKey()).willReturn("channel-billing");

        BillingKeyPrepareResponse response = billingKeyFacade.prepare(MEMBER_ID);

        verify(billingKeyService).validateRegistrable(MEMBER_ID);
        assertThat(response.channelKey()).isEqualTo("channel-billing");
        assertThat(response.issueId()).startsWith("billing-").hasSize(40);
        assertThat(response.issueName()).isEqualTo("카드 등록");
        assertThat(response.customerId()).isEqualTo("member-7");
    }

    @Test
    @DisplayName("PortOne에서 발급 완료와 본인 고객 ID를 확인하면 카드를 저장한다")
    void register_success() {
        BillingKeyInfo info = issued("member-7");
        BillingKeyResponse saved = new BillingKeyResponse(1L, "신한카드", "1234", CardType.CREDIT, true,
                LocalDateTime.of(2026, 11, 4, 10, 0));
        given(billingKeyGateway.getBillingKey(BILLING_KEY)).willReturn(Optional.of(info));
        given(billingKeyService.register(MEMBER_ID, info)).willReturn(saved);

        assertThat(billingKeyFacade.register(MEMBER_ID, REQUEST)).isEqualTo(saved);
    }

    @Test
    @DisplayName("PortOne에 없는 빌링키면 저장하지 않고 확인 실패로 거절한다")
    void register_throwException_whenNotFoundAtPg() {
        given(billingKeyGateway.getBillingKey(BILLING_KEY)).willReturn(Optional.empty());

        assertNotVerified();
        verify(billingKeyService, never()).register(anyLong(), any());
    }

    @Test
    @DisplayName("삭제된 빌링키면 저장하지 않고 확인 실패로 거절한다")
    void register_throwException_whenDeletedAtPg() {
        given(billingKeyGateway.getBillingKey(BILLING_KEY)).willReturn(Optional.of(
                new BillingKeyInfo(BILLING_KEY, false, "member-7", null, null, null)));

        assertNotVerified();
        verify(billingKeyService, never()).register(anyLong(), any());
    }

    @Test
    @DisplayName("PortOne 조회가 실패하면 저장하지 않고 확인 실패로 거절한다")
    void register_throwException_whenPgLookupFails() {
        willThrow(new RuntimeException("timeout")).given(billingKeyGateway).getBillingKey(BILLING_KEY);

        assertNotVerified();
        verify(billingKeyService, never()).register(anyLong(), any());
    }

    @Test
    @DisplayName("다른 회원의 빌링키면 PortOne에서 지우지 않고 거절만 한다")
    void register_throwException_whenOtherCustomer() {
        given(billingKeyGateway.getBillingKey(BILLING_KEY)).willReturn(Optional.of(issued("member-8")));

        assertNotVerified();
        verify(billingKeyService, never()).register(anyLong(), any());
        verify(billingKeyGateway, never()).deleteBillingKey(anyString(), anyString());
    }

    @Test
    @DisplayName("저장 시점에 5장 한도를 넘으면 방금 발급된 본인 빌링키를 PortOne에서 지우고 한도 초과로 거절한다")
    void register_deletePgBillingKey_whenLimitExceeded() {
        BillingKeyInfo info = issued("member-7");
        given(billingKeyGateway.getBillingKey(BILLING_KEY)).willReturn(Optional.of(info));
        given(billingKeyService.register(MEMBER_ID, info))
                .willThrow(new BusinessException(ErrorCode.BILLING_KEY_LIMIT_EXCEEDED));

        assertThatThrownBy(() -> billingKeyFacade.register(MEMBER_ID, REQUEST))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.BILLING_KEY_LIMIT_EXCEEDED);
        verify(billingKeyGateway).deleteBillingKey(eq(BILLING_KEY), anyString());
    }

    @Test
    @DisplayName("한도 초과 뒤 PortOne 삭제까지 실패해도 한도 초과 오류로 응답한다")
    void register_throwLimitExceeded_whenPgDeleteFails() {
        BillingKeyInfo info = issued("member-7");
        given(billingKeyGateway.getBillingKey(BILLING_KEY)).willReturn(Optional.of(info));
        given(billingKeyService.register(MEMBER_ID, info))
                .willThrow(new BusinessException(ErrorCode.BILLING_KEY_LIMIT_EXCEEDED));
        willThrow(new RuntimeException("PG 오류")).given(billingKeyGateway).deleteBillingKey(anyString(), anyString());

        assertThatThrownBy(() -> billingKeyFacade.register(MEMBER_ID, REQUEST))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.BILLING_KEY_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("다른 이유로 저장이 거절되면 PortOne 빌링키는 지우지 않는다")
    void register_keepPgBillingKey_whenOtherRejection() {
        BillingKeyInfo info = issued("member-7");
        given(billingKeyGateway.getBillingKey(BILLING_KEY)).willReturn(Optional.of(info));
        given(billingKeyService.register(MEMBER_ID, info))
                .willThrow(new BusinessException(ErrorCode.BILLING_KEY_NOT_VERIFIED));

        assertNotVerified();
        verify(billingKeyGateway, never()).deleteBillingKey(anyString(), anyString());
    }

    private void assertNotVerified() {
        assertThatThrownBy(() -> billingKeyFacade.register(MEMBER_ID, REQUEST))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.BILLING_KEY_NOT_VERIFIED);
    }

    private BillingKeyInfo issued(String customerId) {
        return new BillingKeyInfo(BILLING_KEY, true, customerId, "신한카드", "53275012****1234", CardType.CREDIT);
    }
}
