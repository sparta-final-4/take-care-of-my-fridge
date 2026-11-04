package org.example.takecareofmyfridgebe.domain.payment.service;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.member.repository.MemberRepository;
import org.example.takecareofmyfridgebe.domain.payment.dto.BillingKeyResponse;
import org.example.takecareofmyfridgebe.domain.payment.entity.BillingKey;
import org.example.takecareofmyfridgebe.domain.payment.entity.BillingKeyStatus;
import org.example.takecareofmyfridgebe.domain.payment.port.BillingKeyInfo;
import org.example.takecareofmyfridgebe.domain.payment.repository.BillingKeyRepository;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BillingKeyService {

    static final int MAX_CARDS = 5;

    private final BillingKeyRepository billingKeyRepository;
    private final MemberRepository memberRepository;

    // 등록 창을 열기 전에 막는다. 동시 등록은 저장할 때 회원 행 잠금으로 다시 막는다
    public void validateRegistrable(Long memberId) {
        validateLimit(billingKeyRepository.countByMemberIdAndStatus(memberId, BillingKeyStatus.ACTIVE));
    }

    // info는 PG에서 발급 완료와 본인 고객 ID를 확인한 빌링키다.
    // 카드가 0장이어도 동시 등록을 하나씩 처리하도록 카드 행이 아니라 회원 행을 잠근다. 잠금이 트랜잭션의 첫 조회라 뒤의 조회는 최신 값을 읽는다
    @Transactional
    public BillingKeyResponse register(Long memberId, BillingKeyInfo info) {
        lockMember(memberId);

        // 같은 등록 확정이 두 번 오면 처음 저장한 카드를 돌려준다
        Optional<BillingKey> existing = billingKeyRepository.findByBillingKey(info.billingKey());
        if (existing.isPresent()) {
            BillingKey card = existing.get();
            if (card.getMemberId().equals(memberId) && card.isActive()) {
                return BillingKeyResponse.from(card);
            }
            throw new BusinessException(ErrorCode.BILLING_KEY_NOT_VERIFIED);
        }

        long activeCount = billingKeyRepository.countByMemberIdAndStatus(memberId, BillingKeyStatus.ACTIVE);
        validateLimit(activeCount);
        BillingKey card = billingKeyRepository.save(BillingKey.builder()
                .memberId(memberId)
                .billingKey(info.billingKey())
                .cardName(info.cardName())
                .cardLast4(info.cardLast4())
                .cardType(info.cardType())
                .isDefault(activeCount == 0) // 첫 카드는 대표 카드
                .build());
        return BillingKeyResponse.from(card);
    }

    // 대표 카드는 회원당 하나다. 회원 행을 잠가 동시에 지정해도 대표가 둘이 되지 않게 한다
    @Transactional
    public List<BillingKeyResponse> setDefault(Long memberId, Long cardId) {
        lockMember(memberId);
        BillingKey target = getActiveCard(memberId, cardId);
        if (!target.isDefault()) {
            billingKeyRepository.findCards(memberId, BillingKeyStatus.ACTIVE).stream()
                    .filter(BillingKey::isDefault)
                    .forEach(BillingKey::unmarkDefault);
            target.markDefault();
        }
        return getCards(memberId);
    }

    // 우리 쪽을 먼저 삭제해 결제에 쓰이지 않게 한다. PortOne 빌링키 삭제는 커밋 뒤에 따로 한다.
    // 대표 카드를 지웠으면 남은 카드 중 가장 최근에 등록한 카드가 대표를 이어받는다. 삭제한 카드를 돌려준다
    @Transactional
    public BillingKey delete(Long memberId, Long cardId, LocalDateTime deletedAt) {
        lockMember(memberId);
        BillingKey card = getActiveCard(memberId, cardId);
        boolean wasDefault = card.isDefault();
        card.delete(deletedAt);
        if (wasDefault) {
            // 지운 카드는 후보에서 직접 뺀다(삭제가 조회 전에 반영되는지에 기대지 않는다). 대표가 없으니 최근 등록순 첫 카드다
            billingKeyRepository.findCards(memberId, BillingKeyStatus.ACTIVE).stream()
                    .filter(candidate -> !candidate.getId().equals(cardId))
                    .findFirst()
                    .ifPresent(BillingKey::markDefault);
        }
        return card;
    }

    // PortOne에서 빌링키 삭제를 확인했을 때 기록한다. 이미 기록돼 있으면 처음 시각을 지킨다
    @Transactional
    public void markPgDeleted(Long cardId, LocalDateTime pgDeletedAt) {
        billingKeyRepository.findById(cardId).ifPresent(card -> card.markPgDeleted(pgDeletedAt));
    }

    public List<BillingKey> findPgDeletePending(Long afterId, int size) {
        return billingKeyRepository.findPgDeletePending(BillingKeyStatus.DELETED, afterId, PageRequest.of(0, size));
    }

    // 회원 단위로 카드 변경을 하나씩 처리한다. 잠금이 트랜잭션의 첫 조회라 뒤의 조회는 최신 값을 읽는다
    private void lockMember(Long memberId) {
        memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    // 남의 카드·삭제한 카드·없는 카드는 구분하지 않고 같은 오류로 답한다
    private BillingKey getActiveCard(Long memberId, Long cardId) {
        return billingKeyRepository.findByIdAndMemberIdAndStatus(cardId, memberId, BillingKeyStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.BILLING_KEY_NOT_FOUND));
    }

    private void validateLimit(long activeCount) {
        if (activeCount >= MAX_CARDS) {
            throw new BusinessException(ErrorCode.BILLING_KEY_LIMIT_EXCEEDED);
        }
    }

    public List<BillingKeyResponse> getCards(Long memberId) {
        return billingKeyRepository.findCards(memberId, BillingKeyStatus.ACTIVE).stream()
                .map(BillingKeyResponse::from)
                .toList();
    }
}
