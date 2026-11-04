package org.example.takecareofmyfridgebe.domain.payment.entity;

import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BillingKeyTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 4, 10, 0);

    private final BillingKey billingKey = BillingKey.builder().memberId(1L).billingKey("billing-key-1")
            .cardName("신한카드 Deep Dream").cardLast4("1234").cardType(CardType.CREDIT).isDefault(true).build();

    @Test
    @DisplayName("카드를 만들면 사용 중 상태로 저장할 값이 그대로 담긴다")
    void build_createActiveBillingKey() {
        assertThat(billingKey.getStatus()).isEqualTo(BillingKeyStatus.ACTIVE);
        assertThat(billingKey.isActive()).isTrue();
        assertThat(billingKey.isDefault()).isTrue();
        assertThat(billingKey.getMemberId()).isEqualTo(1L);
        assertThat(billingKey.getCardLast4()).isEqualTo("1234");
        assertThat(billingKey.getDeletedAt()).isNull();
        assertThat(billingKey.getPgDeletedAt()).isNull();
    }

    @Test
    @DisplayName("사용 중 카드를 삭제하면 삭제 상태와 삭제 시각이 남고 대표 카드에서 빠진다")
    void delete_markDeletedAndUnmarkDefault() {
        billingKey.delete(NOW);

        assertThat(billingKey.getStatus()).isEqualTo(BillingKeyStatus.DELETED);
        assertThat(billingKey.isActive()).isFalse();
        assertThat(billingKey.getDeletedAt()).isEqualTo(NOW);
        assertThat(billingKey.isDefault()).isFalse();
        assertThat(billingKey.getPgDeletedAt()).isNull();
    }

    @Test
    @DisplayName("이미 삭제한 카드를 다시 삭제하면 상태 변경 오류가 난다")
    void delete_throwException_whenAlreadyDeleted() {
        billingKey.delete(NOW);

        assertThatThrownBy(() -> billingKey.delete(NOW.plusMinutes(1)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_BILLING_KEY_STATUS);
    }

    @Test
    @DisplayName("대표가 아닌 사용 중 카드를 대표로 지정하면 대표 카드가 되고, 해제하면 대표에서 빠진다")
    void markDefault_switchDefault() {
        BillingKey other = BillingKey.builder().memberId(1L).billingKey("billing-key-2").isDefault(false).build();

        other.markDefault();
        assertThat(other.isDefault()).isTrue();

        other.unmarkDefault();
        assertThat(other.isDefault()).isFalse();
    }

    @Test
    @DisplayName("삭제한 카드를 대표로 지정하면 상태 변경 오류가 난다")
    void markDefault_throwException_whenDeleted() {
        billingKey.delete(NOW);

        assertThatThrownBy(billingKey::markDefault)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_BILLING_KEY_STATUS);
    }

    @Test
    @DisplayName("삭제한 카드의 PortOne 빌링키 삭제를 확인하면 확인 시각이 남는다")
    void markPgDeleted_recordPgDeletedAt_whenDeleted() {
        billingKey.delete(NOW);

        billingKey.markPgDeleted(NOW.plusMinutes(1));

        assertThat(billingKey.getPgDeletedAt()).isEqualTo(NOW.plusMinutes(1));
    }

    @Test
    @DisplayName("PortOne 빌링키 삭제를 다시 기록해도 처음 확인한 시각이 남는다")
    void markPgDeleted_keepFirstPgDeletedAt_whenCalledAgain() {
        billingKey.delete(NOW);
        billingKey.markPgDeleted(NOW.plusMinutes(1));

        billingKey.markPgDeleted(NOW.plusHours(1));

        assertThat(billingKey.getPgDeletedAt()).isEqualTo(NOW.plusMinutes(1));
    }

    @Test
    @DisplayName("삭제한 카드를 대표에서 빼려 하면 상태 변경 오류가 난다")
    void unmarkDefault_throwException_whenDeleted() {
        billingKey.delete(NOW);

        assertThatThrownBy(billingKey::unmarkDefault)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_BILLING_KEY_STATUS);
    }

    @Test
    @DisplayName("사용 중 카드에 PortOne 빌링키 삭제를 기록하면 상태 변경 오류가 난다")
    void markPgDeleted_throwException_whenActive() {
        assertThatThrownBy(() -> billingKey.markPgDeleted(NOW))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_BILLING_KEY_STATUS);
    }

    @Test
    @DisplayName("마스킹 번호와 발급사 코드가 모두 같으면 같은 카드다")
    void isSameCard_returnTrue_whenNumberAndIssuerMatch() {
        BillingKey card = BillingKey.builder().memberId(1L).billingKey("billing-key-9")
                .cardNumberMasked("53275012****1234").cardIssuerCode("SHINHAN_CARD").build();

        assertThat(card.isSameCard("53275012****1234", "SHINHAN_CARD")).isTrue();
    }

    @Test
    @DisplayName("마스킹 번호나 발급사 코드가 다르거나, 두 값을 모르는 카드는 같은 카드로 보지 않는다")
    void isSameCard_returnFalse_whenDifferentOrUnknown() {
        BillingKey card = BillingKey.builder().memberId(1L).billingKey("billing-key-9")
                .cardNumberMasked("53275012****1234").cardIssuerCode("SHINHAN_CARD").build();
        BillingKey unknown = BillingKey.builder().memberId(1L).billingKey("billing-key-8").build();

        assertThat(card.isSameCard("53275012****5678", "SHINHAN_CARD")).isFalse();
        assertThat(card.isSameCard("53275012****1234", "KOOKMIN_CARD")).isFalse();
        assertThat(unknown.isSameCard(null, null)).isFalse();
    }
}
