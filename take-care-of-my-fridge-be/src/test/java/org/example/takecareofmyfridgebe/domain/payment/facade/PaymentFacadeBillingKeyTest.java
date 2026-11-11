package org.example.takecareofmyfridgebe.domain.payment.facade;

import org.example.takecareofmyfridgebe.domain.order.entity.Order;
import org.example.takecareofmyfridgebe.domain.payment.dto.BillingKeyPaymentRequest;
import org.example.takecareofmyfridgebe.domain.payment.dto.PaymentApproveResult;
import org.example.takecareofmyfridgebe.domain.payment.dto.PaymentConfirmResponse;
import org.example.takecareofmyfridgebe.domain.payment.entity.BillingKey;
import org.example.takecareofmyfridgebe.domain.payment.entity.FailReason;
import org.example.takecareofmyfridgebe.domain.payment.entity.Payment;
import org.example.takecareofmyfridgebe.domain.payment.entity.PaymentChangedBy;
import org.example.takecareofmyfridgebe.domain.payment.entity.PgProvider;
import org.example.takecareofmyfridgebe.domain.payment.port.BillingKeyGateway;
import org.example.takecareofmyfridgebe.domain.payment.port.BillingKeyPayResult;
import org.example.takecareofmyfridgebe.domain.payment.port.PaymentGateway;
import org.example.takecareofmyfridgebe.domain.payment.port.PaymentGatewayResponse;
import org.example.takecareofmyfridgebe.domain.payment.service.BillingKeyCrypto;
import org.example.takecareofmyfridgebe.domain.payment.service.BillingKeyCryptoFixture;
import org.example.takecareofmyfridgebe.domain.payment.service.BillingKeyService;
import org.example.takecareofmyfridgebe.domain.payment.service.PaymentCommandService;
import org.example.takecareofmyfridgebe.domain.payment.service.PaymentHistoryRecorder;
import org.example.takecareofmyfridgebe.domain.payment.service.PaymentPinService;
import org.example.takecareofmyfridgebe.domain.payment.service.PaymentService;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// 등록 카드 결제: 결제 생성 → PortOne 빌링키 결제 → 결과별 승인·실패·대기
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentFacadeBillingKeyTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long ORDER_ID = 100L;
    private static final Long CARD_ID = 7L;
    private static final String PORTONE_PAYMENT_ID = "payment-1";
    private static final String BILLING_KEY = "billing-key-1";
    private static final long AMOUNT = 10000L;
    private static final LocalDateTime EXPIRES_AT = LocalDateTime.now().plusMinutes(10);
    private static final BillingKeyPaymentRequest REQUEST = new BillingKeyPaymentRequest(CARD_ID, "271828");

    @Mock
    private PaymentService paymentService;

    @Mock
    private PaymentCommandService paymentCommandService;

    @Mock
    private PaymentGateway paymentGateway;

    @Mock
    private PaymentHistoryRecorder paymentHistoryRecorder;

    @Mock
    private BillingKeyService billingKeyService;

    @Spy
    private BillingKeyCrypto billingKeyCrypto = BillingKeyCryptoFixture.crypto();

    @Mock
    private BillingKeyGateway billingKeyGateway;

    @Mock
    private PaymentPinService paymentPinService;

    @InjectMocks
    private PaymentFacade paymentFacade;

    private BillingKey card;
    private Payment payment;

    @BeforeEach
    void setUp() {
        card = BillingKey.builder().memberId(MEMBER_ID)
                .encryptedBillingKey(BillingKeyCryptoFixture.encrypted(MEMBER_ID, BILLING_KEY)).isDefault(true).build();
        ReflectionTestUtils.setField(card, "id", CARD_ID);
        Order order = Order.builder().orderNumber("20261104-000001").memberId(MEMBER_ID).totalAmount(AMOUNT).build();
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        payment = Payment.builder().order(order).billingKey(card).portonePaymentId(PORTONE_PAYMENT_ID).amount(AMOUNT)
                .pgProvider(PgProvider.TOSSPAYMENTS).expiresAt(EXPIRES_AT).build();

        given(billingKeyService.getUsableCard(MEMBER_ID, CARD_ID)).willReturn(card);
        given(paymentCommandService.create(ORDER_ID, PgProvider.TOSSPAYMENTS, card)).willReturn(payment);
        given(paymentService.getOrderName(ORDER_ID)).willReturn("대파 외 1건");
    }

    private void givenPay(BillingKeyPayResult result) {
        given(billingKeyGateway.pay(PORTONE_PAYMENT_ID, BILLING_KEY, "대파 외 1건", AMOUNT, "member-1")).willReturn(result);
    }

    private void givenPgPaid(long amount) {
        given(paymentGateway.getPayment(PORTONE_PAYMENT_ID)).willReturn(Optional.of(
                new PaymentGatewayResponse(PORTONE_PAYMENT_ID, "PAID", amount, 0L, EXPIRES_AT.minusMinutes(5))));
    }

    private PaymentConfirmResponse givenApproved() {
        PaymentConfirmResponse approved = mock(PaymentConfirmResponse.class);
        given(paymentCommandService.approve(eq(payment), any(), eq(PaymentChangedBy.USER)))
                .willReturn(PaymentApproveResult.approved(approved));
        return approved;
    }

    private void assertError(ErrorCode errorCode) {
        assertThatThrownBy(() -> paymentFacade.payWithBillingKey(MEMBER_ID, ORDER_ID, REQUEST))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(errorCode);
    }

    @Test
    @DisplayName("등록 카드로 결제되면 토스페이먼츠 결제를 카드와 함께 만들고 PG를 다시 조회해 승인한다")
    void payWithBillingKey_success() {
        givenPay(BillingKeyPayResult.PAID);
        givenPgPaid(AMOUNT);
        PaymentConfirmResponse approved = givenApproved();

        PaymentConfirmResponse response = paymentFacade.payWithBillingKey(MEMBER_ID, ORDER_ID, REQUEST);

        assertThat(response).isSameAs(approved);
        verify(paymentService).validatePayableOrder(MEMBER_ID, ORDER_ID);
        verify(paymentCommandService).create(ORDER_ID, PgProvider.TOSSPAYMENTS, card);
        verify(paymentCommandService, never()).fail(any(), any(), any());
    }

    @Test
    @DisplayName("결제됐어도 PG 금액이 주문 금액과 다르면 실패 처리하고 PG를 취소한다")
    void payWithBillingKey_failAndCancelPg_whenAmountMismatch() {
        givenPay(BillingKeyPayResult.PAID);
        givenPgPaid(AMOUNT + 1);
        given(paymentCommandService.fail(payment, FailReason.AMOUNT_MISMATCH, PaymentChangedBy.USER)).willReturn(true);

        assertError(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        verify(paymentGateway).cancelPayment(eq(PORTONE_PAYMENT_ID), anyString(), isNull());
        verify(paymentCommandService, never()).approve(any(), any(), any());
    }

    @Test
    @DisplayName("카드가 거절되고 PG에도 결제가 없으면 결제를 PG 실패로 처리하고 카드 결제 거절로 응답한다")
    void payWithBillingKey_failAndThrowException_whenRejected() {
        givenPay(BillingKeyPayResult.REJECTED);
        given(paymentGateway.getPayment(PORTONE_PAYMENT_ID)).willReturn(Optional.empty());

        assertError(ErrorCode.BILLING_PAYMENT_REJECTED);
        verify(paymentCommandService).fail(payment, FailReason.PG_FAILED, PaymentChangedBy.USER);
    }

    @Test
    @DisplayName("거절 응답을 받았어도 PG에서 결제가 확인되면 승인한다(PG 내부 타임아웃도 거절 형식으로 올 수 있다)")
    void payWithBillingKey_approve_whenRejectedButPaidAtPg() {
        givenPay(BillingKeyPayResult.REJECTED);
        givenPgPaid(AMOUNT);
        PaymentConfirmResponse approved = givenApproved();

        assertThat(paymentFacade.payWithBillingKey(MEMBER_ID, ORDER_ID, REQUEST)).isSameAs(approved);
        verify(paymentCommandService, never()).fail(any(), any(), any());
    }

    @Test
    @DisplayName("결제 결과를 모를 때 PG가 실패로 확정했으면 실패 처리하고 카드 결제 거절로 바로 응답한다")
    void payWithBillingKey_failAndThrowException_whenUnknownAndFailedAtPg() {
        givenPay(BillingKeyPayResult.UNKNOWN);
        given(paymentGateway.getPayment(PORTONE_PAYMENT_ID)).willReturn(Optional.of(
                new PaymentGatewayResponse(PORTONE_PAYMENT_ID, "FAILED", AMOUNT, 0L, null)));

        assertError(ErrorCode.BILLING_PAYMENT_REJECTED);
        verify(paymentCommandService).fail(payment, FailReason.PG_FAILED, PaymentChangedBy.USER);
    }

    @Test
    @DisplayName("같은 주문의 등록 카드 결제가 진행 중이면 밀어내지 않고 진행 중으로 거절해 두 번 청구되지 않게 한다")
    void payWithBillingKey_throwException_whenBillingPaymentInProgress() {
        Payment inFlight = Payment.builder().order(payment.getOrder()).billingKey(card)
                .portonePaymentId("payment-in-flight").amount(AMOUNT).pgProvider(PgProvider.TOSSPAYMENTS)
                .expiresAt(EXPIRES_AT).build();
        given(paymentService.findReady(ORDER_ID)).willReturn(List.of(inFlight));
        given(paymentGateway.getPayment("payment-in-flight")).willReturn(Optional.empty());

        assertError(ErrorCode.PAYMENT_IN_PROGRESS);
        verify(paymentCommandService, never()).fail(any(), any(), any());
        verify(paymentCommandService, never()).create(anyLong(), any(), any());
    }

    @Test
    @DisplayName("저장된 빌링키를 풀지 못하면 이전 결제 대기 건을 닫지 않고, 결제를 만들지 않고, 카드 사용 불가 서버 오류로 응답한다")
    void throwsBillingKeyUnavailable_whenBillingKeyCannotBeDecrypted() {
        // 다른 회원 ID로 묶인 암호문이라 이 카드 주인 ID로는 풀리지 않는다
        BillingKey broken = BillingKey.builder().memberId(MEMBER_ID)
                .encryptedBillingKey(BillingKeyCryptoFixture.encrypted(MEMBER_ID + 1, BILLING_KEY)).build();
        given(billingKeyService.getUsableCard(MEMBER_ID, CARD_ID)).willReturn(broken);

        assertError(ErrorCode.BILLING_KEY_UNAVAILABLE);
        verify(paymentService, never()).findReady(anyLong());
        verify(paymentCommandService, never()).create(anyLong(), any(), any());
        verify(billingKeyGateway, never()).pay(any(), any(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("결제 결과를 모를 때 PG 조회로 결제가 확인되면 승인한다")
    void payWithBillingKey_approve_whenUnknownButPaidAtPg() {
        givenPay(BillingKeyPayResult.UNKNOWN);
        givenPgPaid(AMOUNT);
        PaymentConfirmResponse approved = givenApproved();

        assertThat(paymentFacade.payWithBillingKey(MEMBER_ID, ORDER_ID, REQUEST)).isSameAs(approved);
    }

    @Test
    @DisplayName("결제 결과를 모르고 PG에도 결제가 없으면 결제 대기로 두고 결과 확인 중으로 응답한다")
    void payWithBillingKey_keepReady_whenUnknownAndNotPaidAtPg() {
        givenPay(BillingKeyPayResult.UNKNOWN);
        given(paymentGateway.getPayment(PORTONE_PAYMENT_ID)).willReturn(Optional.empty());

        assertError(ErrorCode.PAYMENT_RESULT_PENDING);
        verify(paymentCommandService, never()).fail(any(), any(), any());
        verify(paymentCommandService, never()).approve(any(), any(), any());
    }

    @Test
    @DisplayName("결제 결과를 모르고 PG 조회까지 실패하면 결제 대기로 두고 결과 확인 중으로 응답한다")
    void payWithBillingKey_keepReady_whenUnknownAndPgLookupFails() {
        givenPay(BillingKeyPayResult.UNKNOWN);
        willThrow(new RuntimeException("timeout")).given(paymentGateway).getPayment(PORTONE_PAYMENT_ID);

        assertError(ErrorCode.PAYMENT_RESULT_PENDING);
        verify(paymentCommandService, never()).fail(any(), any(), any());
    }

    @Test
    @DisplayName("내 사용 중 카드가 아니면 결제를 만들지 않고 카드 없음으로 거절한다")
    void payWithBillingKey_throwException_whenCardNotUsable() {
        given(billingKeyService.getUsableCard(MEMBER_ID, CARD_ID))
                .willThrow(new BusinessException(ErrorCode.BILLING_KEY_NOT_FOUND));

        assertError(ErrorCode.BILLING_KEY_NOT_FOUND);
        verify(paymentCommandService, never()).create(anyLong(), any(), any());
        verify(billingKeyGateway, never()).pay(anyString(), anyString(), anyString(), anyLong(), anyString());
    }

    @Test
    @DisplayName("결제할 수 없는 주문이면 카드 확인·결제 생성 없이 거절한다")
    void payWithBillingKey_throwException_whenOrderNotPayable() {
        willThrow(new BusinessException(ErrorCode.PAYMENT_ORDER_NOT_PAYABLE))
                .given(paymentService).validatePayableOrder(MEMBER_ID, ORDER_ID);

        assertError(ErrorCode.PAYMENT_ORDER_NOT_PAYABLE);
        verify(billingKeyService, never()).getUsableCard(anyLong(), anyLong());
        verify(paymentCommandService, never()).create(anyLong(), any(), any());
    }

    @Test
    @DisplayName("결제 비밀번호를 확인한 뒤에 결제를 만들고 PortOne에 청구한다")
    void payWithBillingKey_verifyPinBeforeCharge() {
        givenPay(BillingKeyPayResult.PAID);
        givenPgPaid(AMOUNT);
        givenApproved();

        paymentFacade.payWithBillingKey(MEMBER_ID, ORDER_ID, REQUEST);

        InOrder inOrder = inOrder(paymentPinService, paymentCommandService,
                billingKeyGateway);
        inOrder.verify(paymentPinService).verify(MEMBER_ID, "271828");
        inOrder.verify(paymentCommandService).create(ORDER_ID, PgProvider.TOSSPAYMENTS, card);
        inOrder.verify(billingKeyGateway).pay(anyString(), anyString(), anyString(), anyLong(), anyString());
    }

    @Test
    @DisplayName("결제 비밀번호가 틀리면 결제를 만들지 않고 PortOne에도 청구하지 않는다")
    void payWithBillingKey_notCharge_whenPinMismatch() {
        willThrow(new BusinessException(ErrorCode.PAYMENT_PIN_MISMATCH))
                .given(paymentPinService).verify(MEMBER_ID, "271828");

        assertError(ErrorCode.PAYMENT_PIN_MISMATCH);

        verify(paymentCommandService, never()).create(anyLong(), any(), any());
        verify(billingKeyGateway, never()).pay(anyString(), anyString(), anyString(), anyLong(), anyString());
    }
}
