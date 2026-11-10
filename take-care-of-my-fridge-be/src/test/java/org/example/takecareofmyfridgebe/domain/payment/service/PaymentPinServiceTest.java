package org.example.takecareofmyfridgebe.domain.payment.service;

import jakarta.persistence.EntityManager;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.payment.dto.PaymentPinChangeRequest;
import org.example.takecareofmyfridgebe.domain.payment.dto.PaymentPinResetRequest;
import org.example.takecareofmyfridgebe.domain.payment.dto.PaymentPinSetRequest;
import org.example.takecareofmyfridgebe.domain.payment.dto.PaymentPinStatusResponse;
import org.example.takecareofmyfridgebe.domain.payment.entity.PaymentPin;
import org.example.takecareofmyfridgebe.domain.payment.repository.PaymentPinRepository;
import org.example.takecareofmyfridgebe.global.config.jpa.JpaAuditingConfig;
import org.example.takecareofmyfridgebe.global.config.security.PasswordEncoderConfig;
import org.example.takecareofmyfridgebe.global.config.jpa.QueryDslConfig;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.example.takecareofmyfridgebe.support.MySqlContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, QueryDslConfig.class, PasswordEncoderConfig.class, PaymentPinService.class, MemberRowLocker.class})
class PaymentPinServiceTest extends MySqlContainerSupport {

    private static final String PIN = "271828";
    private static final String NEW_PIN = "314159";
    private static final String WRONG_PIN = "000123";
    private static final String LOGIN_PASSWORD = "login-password-1!";

    private final PaymentPinService paymentPinService;
    private final PaymentPinRepository paymentPinRepository;
    private final PasswordEncoder passwordEncoder;
    private final EntityManager entityManager;

    private Long memberId;

    @Autowired
    PaymentPinServiceTest(PaymentPinService paymentPinService, PaymentPinRepository paymentPinRepository,
                          PasswordEncoder passwordEncoder, EntityManager entityManager) {
        this.paymentPinService = paymentPinService;
        this.paymentPinRepository = paymentPinRepository;
        this.passwordEncoder = passwordEncoder;
        this.entityManager = entityManager;
    }

    @BeforeEach
    void setUp() {
        Member member = Member.builder().email("pin@example.com")
                .passwordHash(passwordEncoder.encode(LOGIN_PASSWORD)).nickname("비밀번호회원").build();
        entityManager.persist(member);
        memberId = member.getId();
    }

    private PaymentPin savedPin() {
        return paymentPinRepository.findByMemberId(memberId).orElseThrow();
    }

    private void failTimes(int times) {
        for (int i = 0; i < times; i++) {
            assertThatThrownBy(() -> paymentPinService.verify(memberId, WRONG_PIN))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Test
    @DisplayName("결제 비밀번호를 설정하지 않았으면 미설정 상태와 남은 횟수 5번을 돌려준다")
    void getStatus_returnNotSet_whenNoPin() {
        assertThat(paymentPinService.getStatus(memberId)).isEqualTo(new PaymentPinStatusResponse(false, false, 5));
    }

    @Test
    @DisplayName("결제 비밀번호를 설정하면 원래 값이 아닌 해시로 저장하고 설정 상태가 된다")
    void set_saveHash() {
        paymentPinService.set(memberId, new PaymentPinSetRequest(PIN));

        assertThat(savedPin().getPinHash()).isNotEqualTo(PIN);
        assertThat(passwordEncoder.matches(PIN, savedPin().getPinHash())).isTrue();
        assertThat(paymentPinService.getStatus(memberId)).isEqualTo(new PaymentPinStatusResponse(true, false, 5));
    }

    @Test
    @DisplayName("이미 설정했는데 다시 설정하면 이미 설정됨으로 거절한다")
    void set_throwException_whenAlreadySet() {
        paymentPinService.set(memberId, new PaymentPinSetRequest(PIN));

        assertThatThrownBy(() -> paymentPinService.set(memberId, new PaymentPinSetRequest(NEW_PIN)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_PIN_ALREADY_SET);
    }

    @ParameterizedTest
    @ValueSource(strings = {"111111", "123456", "654321", "456789", "987654"})
    @DisplayName("같은 숫자만 쓰거나 1씩 오르내리는 숫자는 쉬운 비밀번호로 거절한다")
    void set_throwException_whenWeak(String weakPin) {
        assertThatThrownBy(() -> paymentPinService.set(memberId, new PaymentPinSetRequest(weakPin)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_PIN_WEAK);
        assertThat(paymentPinRepository.findByMemberId(memberId)).isEmpty();
    }

    @Test
    @DisplayName("비밀번호가 맞으면 통과하고 그 전에 틀린 횟수를 지운다")
    void verify_resetFailedCount_whenMatched() {
        paymentPinService.set(memberId, new PaymentPinSetRequest(PIN));
        failTimes(2);

        assertThatCode(() -> paymentPinService.verify(memberId, PIN)).doesNotThrowAnyException();

        assertThat(savedPin().getFailedCount()).isZero();
    }

    @Test
    @DisplayName("비밀번호가 틀리면 틀린 횟수를 늘리고 남은 횟수를 알려 준다")
    void verify_throwMismatch_whenWrong() {
        paymentPinService.set(memberId, new PaymentPinSetRequest(PIN));

        assertThatThrownBy(() -> paymentPinService.verify(memberId, WRONG_PIN))
                .isInstanceOf(BusinessException.class)
                .hasMessage("결제 비밀번호가 맞지 않아요. (남은 횟수 4번)")
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_PIN_MISMATCH);
        assertThat(savedPin().getFailedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("5번째로 틀리면 잠기고, 잠긴 뒤에는 맞는 비밀번호도 잠김으로 거절한다")
    void verify_lock_whenFailedFiveTimes() {
        paymentPinService.set(memberId, new PaymentPinSetRequest(PIN));
        failTimes(4);

        assertThatThrownBy(() -> paymentPinService.verify(memberId, WRONG_PIN))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_PIN_LOCKED);
        assertThatThrownBy(() -> paymentPinService.verify(memberId, PIN))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_PIN_LOCKED);
        assertThat(paymentPinService.getStatus(memberId)).isEqualTo(new PaymentPinStatusResponse(true, true, 0));
    }

    @Test
    @DisplayName("비밀번호를 설정하지 않고 확인하면 미설정으로 거절한다")
    void verify_throwException_whenNotSet() {
        assertThatThrownBy(() -> paymentPinService.verify(memberId, PIN))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_PIN_NOT_SET);
    }

    @Test
    @DisplayName("현재 비밀번호가 맞으면 새 비밀번호로 바꾼다")
    void change_success() {
        paymentPinService.set(memberId, new PaymentPinSetRequest(PIN));

        paymentPinService.change(memberId, new PaymentPinChangeRequest(PIN, NEW_PIN));

        assertThatCode(() -> paymentPinService.verify(memberId, NEW_PIN)).doesNotThrowAnyException();
        assertThatThrownBy(() -> paymentPinService.verify(memberId, PIN))
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_PIN_MISMATCH);
    }

    @Test
    @DisplayName("현재 비밀번호가 틀리면 바꾸지 않고 틀린 횟수에 넣는다")
    void change_throwMismatch_whenCurrentWrong() {
        paymentPinService.set(memberId, new PaymentPinSetRequest(PIN));

        assertThatThrownBy(() -> paymentPinService.change(memberId, new PaymentPinChangeRequest(WRONG_PIN, NEW_PIN)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_PIN_MISMATCH);
        assertThat(savedPin().getFailedCount()).isEqualTo(1);
        assertThat(passwordEncoder.matches(PIN, savedPin().getPinHash())).isTrue();
    }

    @Test
    @DisplayName("새 비밀번호가 쉬우면 현재 비밀번호를 확인하기 전에 거절해 틀린 횟수를 늘리지 않는다")
    void change_throwWeak_whenNewPinWeak() {
        paymentPinService.set(memberId, new PaymentPinSetRequest(PIN));

        assertThatThrownBy(() -> paymentPinService.change(memberId, new PaymentPinChangeRequest(WRONG_PIN, "123456")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_PIN_WEAK);
        assertThat(savedPin().getFailedCount()).isZero();
    }

    @Test
    @DisplayName("잠긴 비밀번호는 변경할 수 없고 재설정해야 한다")
    void change_throwLocked_whenLocked() {
        paymentPinService.set(memberId, new PaymentPinSetRequest(PIN));
        failTimes(5);

        assertThatThrownBy(() -> paymentPinService.change(memberId, new PaymentPinChangeRequest(PIN, NEW_PIN)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_PIN_LOCKED);
    }

    @Test
    @DisplayName("로그인 비밀번호가 맞으면 새 비밀번호로 재설정하고 잠김을 푼다")
    void reset_unlock_whenLoginPasswordMatched() {
        paymentPinService.set(memberId, new PaymentPinSetRequest(PIN));
        failTimes(5);

        paymentPinService.reset(memberId, new PaymentPinResetRequest(LOGIN_PASSWORD, NEW_PIN));

        assertThat(paymentPinService.getStatus(memberId)).isEqualTo(new PaymentPinStatusResponse(true, false, 5));
        assertThatCode(() -> paymentPinService.verify(memberId, NEW_PIN)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("아직 설정하지 않았어도 로그인 비밀번호로 재설정하면 새로 설정된다")
    void reset_create_whenNotSet() {
        paymentPinService.reset(memberId, new PaymentPinResetRequest(LOGIN_PASSWORD, NEW_PIN));

        assertThat(passwordEncoder.matches(NEW_PIN, savedPin().getPinHash())).isTrue();
    }

    @Test
    @DisplayName("로그인 비밀번호가 맞으면 통과하고 틀리면 로그인 비밀번호 불일치로 거절한다")
    void verifyLoginPassword_throwException_whenWrong() {
        assertThatCode(() -> paymentPinService.verifyLoginPassword(memberId, LOGIN_PASSWORD))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> paymentPinService.verifyLoginPassword(memberId, "wrong-password"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.LOGIN_PASSWORD_MISMATCH);
    }

    @Test
    @DisplayName("예전과 같은 비밀번호로 재설정해도 받아 준다")
    void reset_success_whenSameAsBefore() {
        paymentPinService.set(memberId, new PaymentPinSetRequest(PIN));
        failTimes(5);

        paymentPinService.reset(memberId, new PaymentPinResetRequest(LOGIN_PASSWORD, PIN));

        assertThatCode(() -> paymentPinService.verify(memberId, PIN)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("현재 비밀번호와 같은 비밀번호로 바꾸려 하면 거절하고 틀린 횟수는 늘리지 않는다")
    void change_throwException_whenSameAsCurrent() {
        paymentPinService.set(memberId, new PaymentPinSetRequest(PIN));

        assertThatThrownBy(() -> paymentPinService.change(memberId, new PaymentPinChangeRequest(PIN, PIN)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_PIN_SAME_AS_CURRENT);
        assertThat(savedPin().getFailedCount()).isZero();
    }

    @Test
    @DisplayName("잠긴 상태에서 쉬운 새 비밀번호로 바꾸려 해도 쉬운 비밀번호가 아니라 잠김으로 알려 준다")
    void change_throwLocked_whenLockedAndNewPinWeak() {
        paymentPinService.set(memberId, new PaymentPinSetRequest(PIN));
        failTimes(5);

        assertThatThrownBy(() -> paymentPinService.change(memberId, new PaymentPinChangeRequest(PIN, "111111")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_PIN_LOCKED);
    }
}
