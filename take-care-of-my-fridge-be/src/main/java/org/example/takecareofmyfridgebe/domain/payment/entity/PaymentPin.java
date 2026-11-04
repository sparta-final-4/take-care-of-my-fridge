package org.example.takecareofmyfridgebe.domain.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.takecareofmyfridgebe.global.entity.BaseTimeEntity;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;

import java.time.LocalDateTime;

// 등록 카드 결제 비밀번호. 회원당 하나이고 원래 값은 저장하지 않고 해시만 둔다.
// 비밀번호가 맞는지 비교는 서비스가 해시로 하고, 엔티티는 틀린 횟수와 잠김만 관리한다
@Entity
@Table(name = "payment_pins",
        uniqueConstraints = @UniqueConstraint(name = "uk_payment_pins_member_id", columnNames = "member_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentPin extends BaseTimeEntity {

    // 이 횟수만큼 연속으로 틀리면 잠근다
    public static final int MAX_FAILED_COUNT = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "pin_hash", nullable = false, length = 100)
    private String pinHash;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    @Builder
    private PaymentPin(Long memberId, String pinHash) {
        this.memberId = memberId;
        this.pinHash = pinHash;
        this.failedCount = 0;
    }

    public boolean isLocked() {
        return lockedAt != null;
    }

    public int remainingAttempts() {
        return Math.max(0, MAX_FAILED_COUNT - failedCount);
    }

    // 비밀번호가 맞았다. 틀린 횟수를 지운다
    public void recordMatched() {
        validateNotLocked();
        this.failedCount = 0;
    }

    // 비밀번호가 틀렸다. 연속으로 MAX_FAILED_COUNT번 틀리면 잠근다
    public void recordFailure(LocalDateTime failedAt) {
        validateNotLocked();
        this.failedCount++;
        if (failedCount >= MAX_FAILED_COUNT) {
            this.lockedAt = failedAt;
        }
    }

    // 현재 비밀번호를 확인한 뒤 바꾼다. 잠겨 있으면 바꿀 수 없고 재설정해야 한다
    public void change(String newPinHash) {
        validateNotLocked();
        this.pinHash = newPinHash;
        this.failedCount = 0;
    }

    // 로그인 비밀번호로 본인을 확인한 뒤 새로 설정한다. 잠김도 함께 푼다
    public void reset(String newPinHash) {
        this.pinHash = newPinHash;
        this.failedCount = 0;
        this.lockedAt = null;
    }

    private void validateNotLocked() {
        if (isLocked()) {
            throw new BusinessException(ErrorCode.PAYMENT_PIN_LOCKED);
        }
    }
}
