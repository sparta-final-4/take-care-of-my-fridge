package org.example.takecareofmyfridgebe.domain.payment.facade;

import org.example.takecareofmyfridgebe.domain.payment.dto.PaymentPinResetRequest;
import org.example.takecareofmyfridgebe.domain.payment.service.PaymentPinResetLimiter;
import org.example.takecareofmyfridgebe.domain.payment.service.PaymentPinService;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PaymentPinFacadeTest {

    private static final Long MEMBER_ID = 7L;
    private static final PaymentPinResetRequest REQUEST = new PaymentPinResetRequest("login-password-1!", "314159");

    @Mock
    private PaymentPinService paymentPinService;

    @Mock
    private PaymentPinResetLimiter paymentPinResetLimiter;

    @InjectMocks
    private PaymentPinFacade paymentPinFacade;

    @Test
    @DisplayName("재설정하면 시도 횟수를 센 뒤 로그인 비밀번호를 확인하고, 맞으면 시도 횟수를 지운 뒤 재설정한다")
    void reset_success() {
        paymentPinFacade.reset(MEMBER_ID, REQUEST);

        InOrder inOrder = inOrder(paymentPinResetLimiter, paymentPinService);
        inOrder.verify(paymentPinResetLimiter).acquire(MEMBER_ID);
        inOrder.verify(paymentPinService).verifyLoginPassword(MEMBER_ID, "login-password-1!");
        inOrder.verify(paymentPinResetLimiter).clear(MEMBER_ID);
        inOrder.verify(paymentPinService).reset(MEMBER_ID, REQUEST);
    }

    @Test
    @DisplayName("쉬운 새 비밀번호면 시도 횟수에 넣지 않고 바로 거절한다")
    void reset_notCountAttempt_whenWeak() {
        assertThatThrownBy(() -> paymentPinFacade.reset(MEMBER_ID, new PaymentPinResetRequest("pw", "111111")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_PIN_WEAK);

        verifyNoInteractions(paymentPinResetLimiter, paymentPinService);
    }

    @Test
    @DisplayName("시도 횟수를 넘었으면 로그인 비밀번호를 확인하지 않고 거절한다")
    void reset_throwException_whenLimited() {
        willThrow(new BusinessException(ErrorCode.PAYMENT_PIN_RESET_LIMITED))
                .given(paymentPinResetLimiter).acquire(MEMBER_ID);

        assertThatThrownBy(() -> paymentPinFacade.reset(MEMBER_ID, REQUEST))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_PIN_RESET_LIMITED);

        verifyNoInteractions(paymentPinService);
    }

    @Test
    @DisplayName("로그인 비밀번호가 틀리면 재설정하지 않고 시도 횟수도 지우지 않는다")
    void reset_keepAttempt_whenLoginPasswordWrong() {
        willThrow(new BusinessException(ErrorCode.LOGIN_PASSWORD_MISMATCH))
                .given(paymentPinService).verifyLoginPassword(MEMBER_ID, "login-password-1!");

        assertThatThrownBy(() -> paymentPinFacade.reset(MEMBER_ID, REQUEST))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LOGIN_PASSWORD_MISMATCH);

        verify(paymentPinService, never()).reset(anyLong(), any());
        verify(paymentPinResetLimiter, never()).clear(anyLong());
    }
}
