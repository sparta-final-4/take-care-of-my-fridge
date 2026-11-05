package org.example.takecareofmyfridgebe.domain.payment.service;

import jakarta.persistence.EntityManager;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.payment.dto.BillingKeyResponse;
import org.example.takecareofmyfridgebe.domain.payment.entity.BillingKey;
import org.example.takecareofmyfridgebe.domain.payment.entity.BillingKeyStatus;
import org.example.takecareofmyfridgebe.domain.payment.entity.CardType;
import org.example.takecareofmyfridgebe.domain.payment.port.BillingKeyInfo;
import org.example.takecareofmyfridgebe.global.config.JpaAuditingConfig;
import org.example.takecareofmyfridgebe.global.config.QueryDslConfig;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.example.takecareofmyfridgebe.support.MySqlContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, QueryDslConfig.class, BillingKeyService.class, MemberRowLocker.class})
class BillingKeyServiceTest extends MySqlContainerSupport {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 4, 12, 0);

    private final BillingKeyService billingKeyService;
    private final EntityManager entityManager;

    private Long memberId;
    private Long otherMemberId;

    @Autowired
    BillingKeyServiceTest(BillingKeyService billingKeyService, EntityManager entityManager) {
        this.billingKeyService = billingKeyService;
        this.entityManager = entityManager;
    }

    @BeforeEach
    void setUp() {
        memberId = member("card@example.com", "카드회원");
        otherMemberId = member("other@example.com", "다른회원");
    }

    @Test
    @DisplayName("첫 카드를 등록하면 대표 카드로 저장하고 카드 정보를 돌려준다")
    void register_saveAsDefault_whenFirstCard() {
        BillingKeyResponse response = billingKeyService.register(memberId, info("billing-key-1"));

        assertThat(response.cardId()).isNotNull();
        assertThat(response.isDefault()).isTrue();
        assertThat(response.cardName()).isEqualTo("신한카드 Deep Dream");
        assertThat(response.cardLast4()).isEqualTo("1234");
        assertThat(response.cardType()).isEqualTo(CardType.CREDIT);
    }

    @Test
    @DisplayName("두 번째 카드부터는 대표 카드가 아니다")
    void register_saveAsNotDefault_whenCardExists() {
        billingKeyService.register(memberId, info("billing-key-1"));

        BillingKeyResponse second = billingKeyService.register(memberId, info("billing-key-2"));

        assertThat(second.isDefault()).isFalse();
    }

    @Test
    @DisplayName("같은 빌링키로 다시 등록하면 새로 저장하지 않고 처음 저장한 카드를 돌려준다")
    void register_returnExisting_whenSameBillingKey() {
        BillingKeyResponse first = billingKeyService.register(memberId, info("billing-key-1"));

        BillingKeyResponse again = billingKeyService.register(memberId, info("billing-key-1"));

        assertThat(again.cardId()).isEqualTo(first.cardId());
        assertThat(billingKeyService.getCards(memberId)).hasSize(1);
    }

    @Test
    @DisplayName("다른 회원이 이미 저장한 빌링키를 등록하면 확인 실패로 거절한다")
    void register_throwException_whenBillingKeyOfOtherMember() {
        billingKeyService.register(otherMemberId, info("billing-key-1"));

        assertThatThrownBy(() -> billingKeyService.register(memberId, info("billing-key-1")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.BILLING_KEY_NOT_VERIFIED);
    }

    @Test
    @DisplayName("사용 중 카드가 5장이면 등록 준비와 등록을 모두 거절한다")
    void register_throwException_whenFiveCards() {
        for (int i = 1; i <= 5; i++) {
            billingKeyService.register(memberId, info("billing-key-" + i));
        }

        assertThatThrownBy(() -> billingKeyService.validateRegistrable(memberId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.BILLING_KEY_LIMIT_EXCEEDED);
        assertThatThrownBy(() -> billingKeyService.register(memberId, info("billing-key-6")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.BILLING_KEY_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("삭제한 카드는 5장 한도와 목록에서 빠진다")
    void getCards_excludeDeleted() {
        for (int i = 1; i <= 5; i++) {
            billingKeyService.register(memberId, info("billing-key-" + i));
        }
        BillingKey deleted = entityManager.createQuery(
                        "select b from BillingKey b where b.billingKey = 'billing-key-3'", BillingKey.class)
                .getSingleResult();
        deleted.delete(LocalDateTime.now());
        entityManager.flush();

        billingKeyService.validateRegistrable(memberId);
        assertThat(billingKeyService.getCards(memberId)).hasSize(4)
                .extracting(BillingKeyResponse::cardId).doesNotContain(deleted.getId());
    }

    @Test
    @DisplayName("카드 목록은 대표 카드를 먼저, 그다음 최근에 등록한 카드 순으로 돌려주고 다른 회원 카드는 넣지 않는다")
    void getCards_orderDefaultFirstThenLatest() {
        BillingKeyResponse first = billingKeyService.register(memberId, info("billing-key-1"));
        BillingKeyResponse second = billingKeyService.register(memberId, info("billing-key-2"));
        BillingKeyResponse third = billingKeyService.register(memberId, info("billing-key-3"));
        billingKeyService.register(otherMemberId, info("billing-key-9"));

        List<BillingKeyResponse> cards = billingKeyService.getCards(memberId);

        assertThat(cards).extracting(BillingKeyResponse::cardId)
                .containsExactly(first.cardId(), third.cardId(), second.cardId());
    }

    @Test
    @DisplayName("없는 회원으로 등록하면 회원 없음 오류가 난다")
    void register_throwException_whenMemberNotFound() {
        assertThatThrownBy(() -> billingKeyService.register(-1L, info("billing-key-1")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("대표가 아닌 카드를 대표로 지정하면 기존 대표 카드는 해제되고 바뀐 목록을 대표 먼저 돌려준다")
    void setDefault_switchDefault() {
        BillingKeyResponse first = billingKeyService.register(memberId, info("billing-key-1"));
        BillingKeyResponse second = billingKeyService.register(memberId, info("billing-key-2"));

        List<BillingKeyResponse> cards = billingKeyService.setDefault(memberId, second.cardId());

        assertThat(cards).extracting(BillingKeyResponse::cardId, BillingKeyResponse::isDefault)
                .containsExactly(tuple(second.cardId(), true), tuple(first.cardId(), false));
    }

    @Test
    @DisplayName("이미 대표인 카드를 다시 대표로 지정해도 대표 카드는 하나다")
    void setDefault_keepSingleDefault_whenAlreadyDefault() {
        BillingKeyResponse first = billingKeyService.register(memberId, info("billing-key-1"));
        billingKeyService.register(memberId, info("billing-key-2"));

        List<BillingKeyResponse> cards = billingKeyService.setDefault(memberId, first.cardId());

        assertThat(cards).filteredOn(BillingKeyResponse::isDefault).extracting(BillingKeyResponse::cardId)
                .containsExactly(first.cardId());
    }

    @Test
    @DisplayName("다른 회원의 카드나 삭제한 카드를 대표로 지정하거나 삭제하면 카드 없음 오류가 난다")
    void setDefaultAndDelete_throwException_whenNotOwnActiveCard() {
        BillingKeyResponse others = billingKeyService.register(otherMemberId, info("billing-key-9"));
        BillingKeyResponse mine = billingKeyService.register(memberId, info("billing-key-1"));
        billingKeyService.delete(memberId, mine.cardId(), NOW);

        for (Long cardId : List.of(others.cardId(), mine.cardId(), -1L)) {
            assertThatThrownBy(() -> billingKeyService.setDefault(memberId, cardId))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.BILLING_KEY_NOT_FOUND);
            assertThatThrownBy(() -> billingKeyService.delete(memberId, cardId, NOW))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.BILLING_KEY_NOT_FOUND);
        }
    }

    @Test
    @DisplayName("대표 카드를 삭제하면 삭제 상태가 되고 남은 카드 중 가장 최근에 등록한 카드가 대표가 된다")
    void delete_passDefaultToLatest_whenDefaultDeleted() {
        BillingKeyResponse first = billingKeyService.register(memberId, info("billing-key-1"));
        billingKeyService.register(memberId, info("billing-key-2"));
        BillingKeyResponse third = billingKeyService.register(memberId, info("billing-key-3"));

        BillingKey deleted = billingKeyService.delete(memberId, first.cardId(), NOW);

        assertThat(deleted.getStatus()).isEqualTo(BillingKeyStatus.DELETED);
        assertThat(deleted.getDeletedAt()).isEqualTo(NOW);
        assertThat(billingKeyService.getCards(memberId)).hasSize(2)
                .filteredOn(BillingKeyResponse::isDefault).extracting(BillingKeyResponse::cardId)
                .containsExactly(third.cardId());
    }

    @Test
    @DisplayName("대표가 아닌 카드를 삭제하면 대표 카드는 그대로다")
    void delete_keepDefault_whenNotDefaultDeleted() {
        BillingKeyResponse first = billingKeyService.register(memberId, info("billing-key-1"));
        BillingKeyResponse second = billingKeyService.register(memberId, info("billing-key-2"));

        billingKeyService.delete(memberId, second.cardId(), NOW);

        assertThat(billingKeyService.getCards(memberId)).extracting(BillingKeyResponse::cardId, BillingKeyResponse::isDefault)
                .containsExactly(tuple(first.cardId(), true));
    }

    @Test
    @DisplayName("마지막 카드를 삭제하면 카드 목록이 비고 PortOne 삭제 대상에 오른다")
    void delete_addPgDeletePending_whenLastCardDeleted() {
        BillingKeyResponse only = billingKeyService.register(memberId, info("billing-key-1"));

        billingKeyService.delete(memberId, only.cardId(), NOW);

        assertThat(billingKeyService.getCards(memberId)).isEmpty();
        assertThat(billingKeyService.findPgDeletePending(0L, 100)).extracting(BillingKey::getId)
                .containsExactly(only.cardId());
    }

    @Test
    @DisplayName("PortOne 삭제를 기록한 카드는 재시도 대상에서 빠지고, 재시도 대상은 지정한 id 뒤부터 읽는다")
    void findPgDeletePending_excludeConfirmedAndReadAfterId() {
        BillingKeyResponse first = billingKeyService.register(memberId, info("billing-key-1"));
        BillingKeyResponse second = billingKeyService.register(memberId, info("billing-key-2"));
        BillingKeyResponse third = billingKeyService.register(memberId, info("billing-key-3"));
        billingKeyService.register(memberId, info("billing-key-4"));
        for (BillingKeyResponse card : List.of(first, second, third)) {
            billingKeyService.delete(memberId, card.cardId(), NOW);
        }

        billingKeyService.markPgDeleted(second.cardId(), NOW.plusMinutes(1));

        assertThat(billingKeyService.findPgDeletePending(0L, 100)).extracting(BillingKey::getId)
                .containsExactly(first.cardId(), third.cardId());
        assertThat(billingKeyService.findPgDeletePending(first.cardId(), 100)).extracting(BillingKey::getId)
                .containsExactly(third.cardId());
        assertThat(billingKeyService.findPgDeletePending(0L, 1)).extracting(BillingKey::getId)
                .containsExactly(first.cardId());
    }

    private BillingKeyInfo info(String billingKey) {
        return new BillingKeyInfo(billingKey, true, "member-" + memberId, "신한카드 Deep Dream",
                cardNumberOf(billingKey), CardType.CREDIT, "SHINHAN_CARD");
    }

    // 빌링키 끝 번호로 서로 다른 카드 번호를 만든다. billing-key-1이면 53275012****1234
    private static String cardNumberOf(String billingKey) {
        int seq = Integer.parseInt(billingKey.substring(billingKey.lastIndexOf('-') + 1));
        return String.format("53275012****%04d", 1233 + seq);
    }

    private Long member(String email, String nickname) {
        Member member = Member.builder().email(email).passwordHash("hash").nickname(nickname).build();
        entityManager.persist(member);
        return member.getId();
    }

    private BillingKeyInfo cardInfo(String billingKey, String numberMasked, String issuerCode) {
        return new BillingKeyInfo(billingKey, true, "member-" + memberId, "신한카드 Deep Dream", numberMasked,
                CardType.CREDIT, issuerCode);
    }

    @Test
    @DisplayName("카드를 등록하면 마스킹 번호와 발급사 코드도 저장한다")
    void register_saveNumberMaskedAndIssuerCode() {
        BillingKeyResponse saved = billingKeyService.register(memberId,
                cardInfo("billing-key-1", "53275012****1234", "SHINHAN_CARD"));

        BillingKey card = entityManager.find(BillingKey.class, saved.cardId());
        assertThat(card.getCardNumberMasked()).isEqualTo("53275012****1234");
        assertThat(card.getCardIssuerCode()).isEqualTo("SHINHAN_CARD");
    }

    @Test
    @DisplayName("같은 카드(마스킹 번호·발급사 같음)를 새 빌링키로 다시 등록하면 이미 등록된 카드로 거절한다")
    void register_throwException_whenSameCardRegistered() {
        billingKeyService.register(memberId, cardInfo("billing-key-1", "53275012****1234", "SHINHAN_CARD"));

        assertThatThrownBy(() -> billingKeyService.register(memberId,
                cardInfo("billing-key-2", "53275012****1234", "SHINHAN_CARD")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.BILLING_KEY_DUPLICATED);
        assertThat(billingKeyService.getCards(memberId)).hasSize(1);
    }

    @Test
    @DisplayName("마스킹 번호나 발급사가 다르면 다른 카드로 보고 등록한다")
    void register_success_whenDifferentCard() {
        billingKeyService.register(memberId, cardInfo("billing-key-1", "53275012****1234", "SHINHAN_CARD"));

        billingKeyService.register(memberId, cardInfo("billing-key-2", "53275012****5678", "SHINHAN_CARD"));
        billingKeyService.register(memberId, cardInfo("billing-key-3", "53275012****1234", "KOOKMIN_CARD"));

        assertThat(billingKeyService.getCards(memberId)).hasSize(3);
    }

    @Test
    @DisplayName("삭제한 카드와 같은 카드나 다른 회원이 등록한 같은 카드는 다시 등록할 수 있다")
    void register_success_whenSameCardDeletedOrOtherMember() {
        BillingKeyResponse first = billingKeyService.register(memberId,
                cardInfo("billing-key-1", "53275012****1234", "SHINHAN_CARD"));
        billingKeyService.delete(memberId, first.cardId(), NOW);

        billingKeyService.register(memberId, cardInfo("billing-key-2", "53275012****1234", "SHINHAN_CARD"));
        billingKeyService.register(otherMemberId, new BillingKeyInfo("billing-key-3", true, "member-" + otherMemberId,
                "신한카드 Deep Dream", "53275012****1234", CardType.CREDIT, "SHINHAN_CARD"));

        assertThat(billingKeyService.getCards(memberId)).hasSize(1);
        assertThat(billingKeyService.getCards(otherMemberId)).hasSize(1);
    }

    @Test
    @DisplayName("발급사 코드를 받지 못해도 마스킹 번호가 같으면 이미 등록된 카드로 거절한다")
    void register_throwException_whenSameNumberWithoutIssuer() {
        billingKeyService.register(memberId, cardInfo("billing-key-1", "53275012****1234", null));

        assertThatThrownBy(() -> billingKeyService.register(memberId,
                cardInfo("billing-key-2", "53275012****1234", null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.BILLING_KEY_DUPLICATED);
    }

    @Test
    @DisplayName("마스킹 번호를 모르는 카드끼리는 같은 카드로 보지 않는다")
    void register_success_whenNumberUnknown() {
        billingKeyService.register(memberId, cardInfo("billing-key-1", null, null));

        billingKeyService.register(memberId, cardInfo("billing-key-2", null, null));

        assertThat(billingKeyService.getCards(memberId)).hasSize(2);
    }
}
