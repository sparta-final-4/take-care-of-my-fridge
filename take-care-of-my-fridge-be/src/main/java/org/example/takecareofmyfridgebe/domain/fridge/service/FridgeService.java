package org.example.takecareofmyfridgebe.domain.fridge.service;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeItemResponse;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeSortType;
import org.example.takecareofmyfridgebe.domain.fridge.entity.StorageType;
import org.example.takecareofmyfridgebe.domain.fridge.repository.FridgeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FridgeService {

    private final FridgeRepository fridgeRepository;

    /**
     * 로그인한 회원의 냉장고 재료 목록을 보관방식, 정렬 기준, 소비기한 임박 여부 조건으로 조회한다.
     *
     * @param memberId     조회 대상 회원 ID
     * @param storage      보관방식 필터 (null이면 전체 조회)
     * @param sort         정렬 기준 (EXPIRATION, CREATED, NAME)
     * @param imminentOnly 소비기한 임박(오늘 ~ 오늘+IMMINENT_DAYS) 재료만 조회할지 여부
     * @return 조건에 맞는 냉장고 재료 응답 목록
     */
    public List<FridgeItemResponse> getItems(
            Long memberId,
            StorageType storage,
            FridgeSortType sort,
            boolean imminentOnly
    ) {
        LocalDate today = LocalDate.now();
        return fridgeRepository.findItemsByCondition(memberId, storage, sort, imminentOnly, today)
                .stream()
                .map(FridgeItemResponse::from)
                .toList();
    }
}
