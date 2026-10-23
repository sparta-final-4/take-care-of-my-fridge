package org.example.takecareofmyfridgebe.domain.sale.service;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventStatus;
import org.example.takecareofmyfridgebe.domain.sale.repository.SaleEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 시각이 된 행사를 열고 닫는다. 상태는 일괄 UPDATE로 바꾸므로 같은 행사를 두 번 처리해도 결과가 같다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SaleEventStatusService {

    private final SaleEventRepository saleEventRepository;

    // 닫기를 먼저 해서, 열기 전에 이미 끝난 예정 행사가 진행 중을 거치지 않고 바로 종료된다
    @Transactional
    public void openAndCloseDue(LocalDateTime now) {
        saleEventRepository.closeDue(List.of(SaleEventStatus.SCHEDULED, SaleEventStatus.ACTIVE), SaleEventStatus.ENDED, now);
        saleEventRepository.openDue(SaleEventStatus.SCHEDULED, SaleEventStatus.ACTIVE, now);
    }
}
