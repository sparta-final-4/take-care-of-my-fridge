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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

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
