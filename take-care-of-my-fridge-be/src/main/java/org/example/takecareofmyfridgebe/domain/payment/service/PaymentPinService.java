package org.example.takecareofmyfridgebe.domain.payment.service;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.member.repository.MemberRepository;
import org.example.takecareofmyfridgebe.domain.payment.dto.PaymentPinChangeRequest;
import org.example.takecareofmyfridgebe.domain.payment.dto.PaymentPinResetRequest;
import org.example.takecareofmyfridgebe.domain.payment.dto.PaymentPinSetRequest;
import org.example.takecareofmyfridgebe.domain.payment.dto.PaymentPinStatusResponse;
import org.example.takecareofmyfridgebe.domain.payment.entity.PaymentPin;
import org.example.takecareofmyfridgebe.domain.payment.repository.PaymentPinRepository;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

// 등록 카드 결제 비밀번호. 쓰기는 모두 회원 행을 잠근 뒤 처리해 같은 회원의 요청을 하나씩 처리한다.
// 동시에 여러 번 틀려도 틀린 횟수가 빠짐없이 쌓여 5번 제한을 넘지 못한다.
// 설정·재설정은 새 비밀번호 해시(BCrypt, 약 100ms)를 잠그기 전에 만들어 잠금을 오래 잡지 않는다
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentPinService {

    private final PaymentPinRepository paymentPinRepository;
    private final MemberRepository memberRepository;
    private final MemberRowLocker memberRowLocker;
    private final PasswordEncoder passwordEncoder;

    public PaymentPinStatusResponse getStatus(Long memberId) {
        return paymentPinRepository.findByMemberId(memberId)
                .map(PaymentPinStatusResponse::from)
                .orElseGet(PaymentPinStatusResponse::notSet);
    }

    // 처음 설정. 이미 있으면 변경이나 재설정을 써야 한다
    @Transactional
    public void set(Long memberId, PaymentPinSetRequest request) {
        PaymentPin.validateStrength(request.pin());
        String pinHash = passwordEncoder.encode(request.pin());
        memberRowLocker.lock(memberId);
        if (paymentPinRepository.findByMemberId(memberId).isPresent()) {
            throw new BusinessException(ErrorCode.PAYMENT_PIN_ALREADY_SET);
        }
        paymentPinRepository.save(request.toEntity(memberId, pinHash));
    }

    // 결제 전에 확인한다. 틀리면 틀린 횟수를 저장한 채로 예외를 던지므로 예외가 나도 롤백하지 않는다
    @Transactional(noRollbackFor = BusinessException.class)
    public void verify(Long memberId, String pin) {
        memberRowLocker.lock(memberId);
        matchOrRecordFailure(getPin(memberId), pin);
    }

    // 현재 비밀번호를 확인한 뒤 바꾼다. 잠김 → 쉬운 비밀번호 → 현재 비밀번호 → 같은 비밀번호 순으로 확인한다.
    // 현재 비밀번호가 틀리면 결제 때와 같이 틀린 횟수에 넣고, 쉬운 새 비밀번호는 그 전에 거절해 횟수를 늘리지 않는다.
    // 새 비밀번호 해시는 거절될 요청에 쓰지 않도록 모든 확인이 끝난 뒤에 만든다
    @Transactional(noRollbackFor = BusinessException.class)
    public void change(Long memberId, PaymentPinChangeRequest request) {
        memberRowLocker.lock(memberId);
        PaymentPin paymentPin = getPin(memberId);
        validateNotLocked(paymentPin);
        PaymentPin.validateStrength(request.newPin());
        matchOrRecordFailure(paymentPin, request.currentPin());
        // 현재 비밀번호가 맞았으니 원래 값끼리 비교하면 된다
        if (request.newPin().equals(request.currentPin())) {
            throw new BusinessException(ErrorCode.PAYMENT_PIN_SAME_AS_CURRENT);
        }
        paymentPin.change(passwordEncoder.encode(request.newPin()));
    }

    // 재설정 전에 로그인 비밀번호로 본인인지 확인한다. 재설정 트랜잭션 밖에서 확인해 재설정의 첫 조회를 회원 행 잠금으로 둔다
    public void verifyLoginPassword(Long memberId, String loginPassword) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        if (!passwordEncoder.matches(loginPassword, member.getPasswordHash())) {
            throw new BusinessException(ErrorCode.LOGIN_PASSWORD_MISMATCH);
        }
    }

    // 새로 설정하고 잠김을 푼다. 아직 설정하지 않았으면 새로 만든다.
    // 로그인 비밀번호 확인과 시도 횟수 제한은 PaymentPinFacade가 먼저 한다.
    // 잊어버린 비밀번호를 다시 정하는 기능이라 예전과 같은 비밀번호도 받는다(같은지 알려 주면 예전 비밀번호를 맞혀 볼 수 있다)
    @Transactional
    public void reset(Long memberId, PaymentPinResetRequest request) {
        PaymentPin.validateStrength(request.newPin());
        String newPinHash = passwordEncoder.encode(request.newPin());
        memberRowLocker.lock(memberId);
        paymentPinRepository.findByMemberId(memberId).ifPresentOrElse(
                paymentPin -> paymentPin.reset(newPinHash),
                () -> paymentPinRepository.save(request.toEntity(memberId, newPinHash)));
    }

    private void matchOrRecordFailure(PaymentPin paymentPin, String pin) {
        validateNotLocked(paymentPin);
        if (passwordEncoder.matches(pin, paymentPin.getPinHash())) {
            paymentPin.recordMatched();
            return;
        }
        paymentPin.recordFailure(LocalDateTime.now());
        validateNotLocked(paymentPin);
        throw new BusinessException(ErrorCode.PAYMENT_PIN_MISMATCH,
                "%s (남은 횟수 %d번)".formatted(ErrorCode.PAYMENT_PIN_MISMATCH.getMessage(),
                        paymentPin.remainingAttempts()));
    }

    private static void validateNotLocked(PaymentPin paymentPin) {
        if (paymentPin.isLocked()) {
            throw new BusinessException(ErrorCode.PAYMENT_PIN_LOCKED);
        }
    }

    private PaymentPin getPin(Long memberId) {
        return paymentPinRepository.findByMemberId(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_PIN_NOT_SET));
    }
}
