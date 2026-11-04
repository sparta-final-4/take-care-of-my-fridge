package org.example.takecareofmyfridgebe.domain.payment.facade;

import org.example.takecareofmyfridgebe.domain.payment.dto.BillingKeyPrepareResponse;
import org.example.takecareofmyfridgebe.domain.payment.dto.BillingKeyRegisterRequest;
import org.example.takecareofmyfridgebe.domain.payment.dto.BillingKeyResponse;
import org.example.takecareofmyfridgebe.domain.payment.entity.BillingKey;
import org.example.takecareofmyfridgebe.domain.payment.entity.CardType;
import org.example.takecareofmyfridgebe.domain.payment.port.BillingKeyGateway;
import org.example.takecareofmyfridgebe.domain.payment.port.BillingKeyInfo;
import org.example.takecareofmyfridgebe.domain.payment.service.BillingKeyService;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
import static org.mockito.Mockito.times;
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

    @Test
    @DisplayName("카드를 삭제하면 우리 쪽 삭제 뒤 PortOne 빌링키를 지우고 삭제 확인 시각을 기록한다")
    void delete_deletePgBillingKeyAndRecord() {
        given(billingKeyService.delete(eq(MEMBER_ID), eq(1L), any())).willReturn(deletedCard(1L));

        billingKeyFacade.delete(MEMBER_ID, 1L);

        verify(billingKeyGateway).deleteBillingKey(eq(BILLING_KEY), anyString());
        verify(billingKeyService).markPgDeleted(eq(1L), any());
    }

    @Test
    @DisplayName("PortOne 빌링키 삭제가 실패해도 카드 삭제는 성공으로 끝나고 확인 시각은 남기지 않는다")
    void delete_success_whenPgDeleteFails() {
        given(billingKeyService.delete(eq(MEMBER_ID), eq(1L), any())).willReturn(deletedCard(1L));
        willThrow(new RuntimeException("PG 오류")).given(billingKeyGateway).deleteBillingKey(anyString(), anyString());

        billingKeyFacade.delete(MEMBER_ID, 1L);

        verify(billingKeyService, never()).markPgDeleted(anyLong(), any());
    }

    @Test
    @DisplayName("PortOne 삭제 뒤 확인 기록이 실패해도 카드 삭제는 성공으로 끝난다")
    void delete_success_whenMarkPgDeletedFails() {
        given(billingKeyService.delete(eq(MEMBER_ID), eq(1L), any())).willReturn(deletedCard(1L));
        willThrow(new RuntimeException("DB 오류")).given(billingKeyService).markPgDeleted(eq(1L), any());

        billingKeyFacade.delete(MEMBER_ID, 1L);

        verify(billingKeyGateway).deleteBillingKey(eq(BILLING_KEY), anyString());
    }

    @Test
    @DisplayName("우리 쪽 삭제가 거절되면 PortOne 빌링키는 지우지 않는다")
    void delete_keepPgBillingKey_whenNotFound() {
        given(billingKeyService.delete(eq(MEMBER_ID), eq(1L), any()))
                .willThrow(new BusinessException(ErrorCode.BILLING_KEY_NOT_FOUND));

        assertThatThrownBy(() -> billingKeyFacade.delete(MEMBER_ID, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.BILLING_KEY_NOT_FOUND);
        verify(billingKeyGateway, never()).deleteBillingKey(anyString(), anyString());
    }

    @Test
    @DisplayName("PortOne 삭제 재시도는 대상을 끝까지 이어 읽고, 한 건이 실패해도 나머지를 지운다")
    void retryPgDeletes_continueOthers_whenOneFails() {
        List<BillingKey> fullPage = new ArrayList<>();
        for (long id = 1; id <= BillingKeyFacade.PG_DELETE_BATCH_SIZE; id++) {
            fullPage.add(deletedCard(id));
        }
        given(billingKeyService.findPgDeletePending(0L, BillingKeyFacade.PG_DELETE_BATCH_SIZE)).willReturn(fullPage);
        given(billingKeyService.findPgDeletePending((long) BillingKeyFacade.PG_DELETE_BATCH_SIZE,
                BillingKeyFacade.PG_DELETE_BATCH_SIZE)).willReturn(List.of(deletedCard(101L)));
        willThrow(new RuntimeException("PG 오류")).willDoNothing()
                .given(billingKeyGateway).deleteBillingKey(anyString(), anyString());

        billingKeyFacade.retryPgDeletes();

        verify(billingKeyGateway, times(101)).deleteBillingKey(anyString(), anyString());
        verify(billingKeyService, never()).markPgDeleted(eq(1L), any());
        verify(billingKeyService).markPgDeleted(eq(2L), any());
        verify(billingKeyService).markPgDeleted(eq(101L), any());
    }

    @Test
    @DisplayName("대표 카드 지정은 서비스 결과를 그대로 돌려준다")
    void setDefault_success() {
        List<BillingKeyResponse> cards = List.of(new BillingKeyResponse(2L, "국민카드", "5678", CardType.DEBIT, true,
                LocalDateTime.of(2026, 11, 4, 10, 0)));
        given(billingKeyService.setDefault(MEMBER_ID, 2L)).willReturn(cards);

        assertThat(billingKeyFacade.setDefault(MEMBER_ID, 2L)).isEqualTo(cards);
    }

    @Test
    @DisplayName("빌링키 발급 웹훅이 오면 PortOne에서 확인하고 고객 ID의 회원으로 카드를 저장한다")
    void registerFromWebhook_success() {
        BillingKeyInfo info = issued("member-7");
        given(billingKeyGateway.getBillingKey(BILLING_KEY)).willReturn(Optional.of(info));

        assertThat(billingKeyFacade.registerFromWebhook(BILLING_KEY)).isTrue();
        verify(billingKeyService).register(MEMBER_ID, info);
    }

    @Test
    @DisplayName("빌링키가 없거나 PortOne에 없거나 삭제됐거나 회원 형식이 아닌 고객 ID면 저장하지 않는다")
    void registerFromWebhook_returnFalse_whenNotRegistrable() {
        assertThat(billingKeyFacade.registerFromWebhook(null)).isFalse();

        given(billingKeyGateway.getBillingKey(BILLING_KEY)).willReturn(Optional.empty());
        assertThat(billingKeyFacade.registerFromWebhook(BILLING_KEY)).isFalse();

        given(billingKeyGateway.getBillingKey(BILLING_KEY)).willReturn(Optional.of(
                new BillingKeyInfo(BILLING_KEY, false, "member-7", null, null, null)));
        assertThat(billingKeyFacade.registerFromWebhook(BILLING_KEY)).isFalse();

        given(billingKeyGateway.getBillingKey(BILLING_KEY)).willReturn(Optional.of(issued("other-service-1")));
        assertThat(billingKeyFacade.registerFromWebhook(BILLING_KEY)).isFalse();

        verify(billingKeyService, never()).register(anyLong(), any());
    }

    @Test
    @DisplayName("빌링키 발급 웹훅에서 5장을 넘으면 저장하지 않고 PortOne 빌링키를 지운다")
    void registerFromWebhook_deletePgBillingKey_whenLimitExceeded() {
        BillingKeyInfo info = issued("member-7");
        given(billingKeyGateway.getBillingKey(BILLING_KEY)).willReturn(Optional.of(info));
        given(billingKeyService.register(MEMBER_ID, info))
                .willThrow(new BusinessException(ErrorCode.BILLING_KEY_LIMIT_EXCEEDED));

        assertThat(billingKeyFacade.registerFromWebhook(BILLING_KEY)).isFalse();
        verify(billingKeyGateway).deleteBillingKey(eq(BILLING_KEY), anyString());
    }

    @Test
    @DisplayName("빌링키 발급 웹훅의 회원이 없으면 저장하지 않고 쓸 사람이 없는 빌링키를 PortOne에서 지운다")
    void registerFromWebhook_deletePgBillingKey_whenMemberNotFound() {
        BillingKeyInfo info = issued("member-7");
        given(billingKeyGateway.getBillingKey(BILLING_KEY)).willReturn(Optional.of(info));
        given(billingKeyService.register(MEMBER_ID, info)).willThrow(new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        assertThat(billingKeyFacade.registerFromWebhook(BILLING_KEY)).isFalse();
        verify(billingKeyGateway).deleteBillingKey(eq(BILLING_KEY), anyString());
    }

    @Test
    @DisplayName("빌링키 발급 웹훅에서 PortOne 조회가 실패하면 예외를 던져 웹훅 재전송으로 다시 시도한다")
    void registerFromWebhook_throwException_whenPgLookupFails() {
        willThrow(new RuntimeException("timeout")).given(billingKeyGateway).getBillingKey(BILLING_KEY);

        assertThatThrownBy(() -> billingKeyFacade.registerFromWebhook(BILLING_KEY)).isInstanceOf(RuntimeException.class);
    }

    private BillingKey deletedCard(Long id) {
        BillingKey card = BillingKey.builder().memberId(MEMBER_ID).billingKey(BILLING_KEY).build();
        ReflectionTestUtils.setField(card, "id", id);
        card.delete(LocalDateTime.of(2026, 11, 4, 10, 0));
        return card;
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
