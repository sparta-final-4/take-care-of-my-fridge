package org.example.takecareofmyfridgebe.domain.payment.facade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.payment.dto.BillingKeyPrepareResponse;
import org.example.takecareofmyfridgebe.domain.payment.dto.BillingKeyRegisterRequest;
import org.example.takecareofmyfridgebe.domain.payment.dto.BillingKeyResponse;
import org.example.takecareofmyfridgebe.domain.payment.entity.BillingKey;
import org.example.takecareofmyfridgebe.domain.payment.port.BillingKeyGateway;
import org.example.takecareofmyfridgebe.domain.payment.port.BillingKeyInfo;
import org.example.takecareofmyfridgebe.domain.payment.service.BillingKeyService;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// PortOne 빌링키 조회·삭제는 트랜잭션 밖에서 하고, 카드 저장은 BillingKeyService에 맡긴다
@Slf4j
@Component
@RequiredArgsConstructor
public class BillingKeyFacade {

    private static final String ISSUE_ID_PREFIX = "billing-";
    private static final String ISSUE_NAME = "카드 등록";
    private static final String CUSTOMER_ID_PREFIX = "member-";
    private static final String LIMIT_EXCEEDED_DELETE_REASON = "카드 등록 한도 초과로 저장하지 않은 빌링키 삭제";
    private static final String CUSTOMER_DELETE_REASON = "회원이 등록 카드 삭제";
    static final int PG_DELETE_BATCH_SIZE = 100;

    private final BillingKeyService billingKeyService;
    private final BillingKeyGateway billingKeyGateway;

    // 카드 등록을 누를 때 프론트가 호출한다. 응답 값으로 PortOne 카드 등록 창을 연다
    public BillingKeyPrepareResponse prepare(Long memberId) {
        billingKeyService.validateRegistrable(memberId);
        return new BillingKeyPrepareResponse(
                billingKeyGateway.billingChannelKey(),
                // 결제대행사에 따라 발급 요청 ID가 필수라 넘긴다. 본인 확인은 고객 ID로 한다
                ISSUE_ID_PREFIX + UUID.randomUUID().toString().replace("-", ""),
                ISSUE_NAME,
                customerId(memberId));
    }

    // 카드 등록 창이 성공으로 끝나면 프론트가 호출한다. 브라우저가 넘긴 빌링키는 믿지 않고 PortOne에서 다시 확인한다
    public BillingKeyResponse register(Long memberId, BillingKeyRegisterRequest request) {
        BillingKeyInfo info = findIssued(request.billingKey());
        // 다른 회원의 빌링키는 그 회원의 정상 카드이므로 지우지 않고 거절만 한다. 카드가 있다는 사실도 드러내지 않는다
        if (!customerId(memberId).equals(info.customerId())) {
            log.warn("본인 것이 아닌 빌링키 등록 요청: memberId={}", memberId);
            throw new BusinessException(ErrorCode.BILLING_KEY_NOT_VERIFIED);
        }
        try {
            return billingKeyService.register(memberId, info);
        } catch (BusinessException e) {
            // 한도 초과로 저장하지 않은 것이 확실할 때만 방금 발급된 본인 빌링키를 PortOne에서 지운다.
            // DB 오류처럼 저장됐는지 모르는 실패에서 지우면 저장된 카드가 결제에 쓸 수 없게 되므로 지우지 않는다(남은 빌링키는 결제에 쓰이지 않는다)
            if (e.getErrorCode() == ErrorCode.BILLING_KEY_LIMIT_EXCEEDED) {
                deleteQuietly(info.billingKey(), LIMIT_EXCEEDED_DELETE_REASON);
            }
            throw e;
        }
    }

    public List<BillingKeyResponse> getCards(Long memberId) {
        return billingKeyService.getCards(memberId);
    }

    public List<BillingKeyResponse> setDefault(Long memberId, Long cardId) {
        return billingKeyService.setDefault(memberId, cardId);
    }

    // 우리 쪽 삭제를 커밋한 뒤 PortOne 빌링키를 지운다. PortOne 삭제가 실패해도 카드는 이미 결제에 쓰이지 않으므로
    // 사용자에게는 삭제 성공으로 답하고, 남은 빌링키는 재시도 스케줄러가 지운다
    public void delete(Long memberId, Long cardId) {
        BillingKey deleted = billingKeyService.delete(memberId, cardId, LocalDateTime.now());
        deleteAtPg(deleted.getId(), deleted.getBillingKey());
    }

    // 우리 쪽에서는 삭제했지만 PortOne 삭제를 확인하지 못한 카드를 다시 지운다. 한 건이 실패해도 나머지는 계속 처리한다
    public void retryPgDeletes() {
        long afterId = 0L;
        while (true) {
            List<BillingKey> pending = billingKeyService.findPgDeletePending(afterId, PG_DELETE_BATCH_SIZE);
            for (BillingKey card : pending) {
                deleteAtPg(card.getId(), card.getBillingKey());
            }
            if (pending.size() < PG_DELETE_BATCH_SIZE) {
                return;
            }
            afterId = pending.get(pending.size() - 1).getId();
        }
    }

    // PortOne에 없거나, 삭제됐거나, 조회 자체가 실패하면 저장하지 않는다
    private BillingKeyInfo findIssued(String billingKey) {
        BillingKeyInfo info;
        try {
            info = billingKeyGateway.getBillingKey(billingKey).orElse(null);
        } catch (RuntimeException e) {
            log.warn("PortOne 빌링키 조회 실패", e);
            throw new BusinessException(ErrorCode.BILLING_KEY_NOT_VERIFIED);
        }
        if (info == null || !info.issued()) {
            log.warn("발급 완료가 아닌 빌링키 등록 요청: found={}", info != null);
            throw new BusinessException(ErrorCode.BILLING_KEY_NOT_VERIFIED);
        }
        return info;
    }

    // PortOne 삭제(이미 삭제됨 포함)가 확인되면 기록한다. 실패하면 기록하지 않아 재시도 대상으로 남는다
    private void deleteAtPg(Long cardId, String billingKey) {
        try {
            billingKeyGateway.deleteBillingKey(billingKey, CUSTOMER_DELETE_REASON);
        } catch (RuntimeException e) {
            log.warn("PortOne 빌링키 삭제 실패, 재시도 대상으로 남김: cardId={}", cardId, e);
            return;
        }
        try {
            billingKeyService.markPgDeleted(cardId, LocalDateTime.now());
        } catch (RuntimeException e) {
            // PortOne에서는 지웠다. 기록만 실패했으므로 재시도 때 PortOne이 "이미 없음"으로 답하면 그때 기록된다
            log.error("PortOne 빌링키는 삭제됐지만 삭제 확인 기록 실패: cardId={}", cardId, e);
        }
    }

    // 삭제가 실패해도 결제에 쓰이지 않는 빌링키라 돈 문제는 없다. 로그만 남긴다
    private void deleteQuietly(String billingKey, String reason) {
        try {
            billingKeyGateway.deleteBillingKey(billingKey, reason);
        } catch (RuntimeException e) {
            log.error("저장하지 않은 빌링키의 PortOne 삭제 실패", e);
        }
    }

    private static String customerId(Long memberId) {
        return CUSTOMER_ID_PREFIX + memberId;
    }
}
