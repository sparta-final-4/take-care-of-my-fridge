package org.example.takecareofmyfridgebe.domain.sale.service;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEvent;
import org.example.takecareofmyfridgebe.domain.sale.repository.SaleEventItemRepository;
import org.example.takecareofmyfridgebe.domain.sale.repository.SaleEventRepository;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 진행 중인 할인 행사를 중간에 닫거나, 시작 전인 할인 행사를 지운다.
// 두 경우 모두 행사 행을 잠근 뒤 상태를 확인하므로, 상태 정리가 같은 할인 행사를 바꾸려 하면 이 요청이 끝날 때까지 기다린다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SaleEventTerminationService {

    private final SaleEventRepository saleEventRepository;
    private final SaleEventItemRepository saleEventItemRepository;

    @Transactional
    public void end(Long saleEventId) {
        SaleEvent saleEvent = findForUpdate(saleEventId);
        LocalDateTime now = LocalDateTime.now(SaleEvent.ZONE);
        if (!saleEvent.isEndable(now)) {
            throw new BusinessException(ErrorCode.SALE_EVENT_NOT_ENDABLE);
        }
        saleEvent.end(now);
    }

    @Transactional
    public void delete(Long saleEventId) {
        SaleEvent saleEvent = findForUpdate(saleEventId);
        if (!saleEvent.isDeletable(LocalDateTime.now(SaleEvent.ZONE))) {
            throw new BusinessException(ErrorCode.SALE_EVENT_NOT_DELETABLE);
        }
        saleEventItemRepository.deleteAll(saleEventItemRepository.findBySaleEventIdOrderById(saleEventId));
        saleEventRepository.delete(saleEvent);
    }

    private SaleEvent findForUpdate(Long saleEventId) {
        return saleEventRepository.findByIdForUpdate(saleEventId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SALE_EVENT_NOT_FOUND));
    }
}
