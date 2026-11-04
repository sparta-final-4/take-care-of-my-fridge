package org.example.takecareofmyfridgebe.domain.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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

// 회원이 등록한 카드. 카드 정보는 PortOne이 보관하고 우리는 빌링키와 화면 표시용 정보만 저장한다
@Entity
@Table(name = "billing_keys",
        uniqueConstraints = @UniqueConstraint(name = "uk_billing_keys_billing_key", columnNames = "billing_key"),
        indexes = {
                @Index(name = "idx_billing_keys_member_status", columnList = "member_id, status"),
                @Index(name = "idx_billing_keys_status_pg_deleted_at", columnList = "status, pg_deleted_at")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BillingKey extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    // PortOne 빌링키. 응답으로 내려주지 않는다
    @Column(name = "billing_key", nullable = false, length = 200)
    private String billingKey;

    // 카드 상품명. PortOne 빌링키 조회의 card.name이며 카드사 이름이 아니다(PortOne은 카드사를 코드로만 준다)
    @Column(name = "card_name", length = 100)
    private String cardName;

    // 카드 번호 끝 4자리. PortOne의 마스킹된 카드 번호(card.number)에서 뽑는다
    @Column(name = "card_last4", columnDefinition = "CHAR(4)")
    private String cardLast4;

    // 마스킹된 카드 번호와 발급사 코드(PortOne card.number, card.issuer). 같은 회원이 같은 카드를 다시 등록하는지 확인한다
    @Column(name = "card_number_masked", length = 30)
    private String cardNumberMasked;

    @Column(name = "card_issuer_code", length = 30)
    private String cardIssuerCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", length = 20)
    private CardType cardType;

    // 대표 카드. 회원당 사용 중 카드 중 하나만 대표가 되도록 서비스가 회원 행을 잠그고 맞춘다
    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BillingKeyStatus status;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // PortOne 빌링키 삭제를 확인한 시각. 삭제 상태인데 비어 있으면 PortOne 삭제를 다시 시도할 대상이다
    @Column(name = "pg_deleted_at")
    private LocalDateTime pgDeletedAt;

    @Builder
    private BillingKey(Long memberId, String billingKey, String cardName, String cardLast4, String cardNumberMasked,
                       String cardIssuerCode, CardType cardType, boolean isDefault) {
        this.memberId = memberId;
        this.billingKey = billingKey;
        this.cardName = cardName;
        this.cardLast4 = cardLast4;
        this.cardNumberMasked = cardNumberMasked;
        this.cardIssuerCode = cardIssuerCode;
        this.cardType = cardType;
        this.isDefault = isDefault;
        this.status = BillingKeyStatus.ACTIVE;
    }

    public boolean isActive() {
        return status == BillingKeyStatus.ACTIVE;
    }

    // 같은 실물 카드인지. 마스킹 번호나 발급사 코드를 모르는 카드(이 값이 생기기 전에 등록한 카드)는 비교하지 않는다
    public boolean isSameCard(String otherNumberMasked, String otherIssuerCode) {
        return cardNumberMasked != null && cardIssuerCode != null
                && cardNumberMasked.equals(otherNumberMasked) && cardIssuerCode.equals(otherIssuerCode);
    }

    public void markDefault() {
        validateActive();
        this.isDefault = true;
    }

    public void unmarkDefault() {
        validateActive();
        this.isDefault = false;
    }

    // 우리 쪽에서 먼저 삭제해 결제에 쓰이지 않게 한다. PortOne 빌링키 삭제는 그 뒤에 따로 한다
    public void delete(LocalDateTime deletedAt) {
        validateActive();
        this.status = BillingKeyStatus.DELETED;
        this.deletedAt = deletedAt;
        this.isDefault = false;
    }

    // 재시도가 겹쳐 다시 불려도 처음 확인한 시각을 지킨다
    public void markPgDeleted(LocalDateTime pgDeletedAt) {
        if (status != BillingKeyStatus.DELETED) {
            throw new BusinessException(ErrorCode.INVALID_BILLING_KEY_STATUS);
        }
        if (this.pgDeletedAt == null) {
            this.pgDeletedAt = pgDeletedAt;
        }
    }

    private void validateActive() {
        if (status != BillingKeyStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.INVALID_BILLING_KEY_STATUS);
        }
    }
}
