package org.example.takecareofmyfridgebe.domain.fridge.service;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeItemResponse;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeItemUpdateRequest;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeRemoveReason;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeSortType;
import org.example.takecareofmyfridgebe.domain.fridge.entity.Fridge;
import org.example.takecareofmyfridgebe.domain.fridge.entity.FridgeItemLog;
import org.example.takecareofmyfridgebe.domain.fridge.repository.FridgeItemLogRepository;
import org.example.takecareofmyfridgebe.domain.fridge.repository.FridgeRepository;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.Ingredient;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FridgeService {

    private final FridgeRepository fridgeRepository;
    private final FridgeItemLogRepository fridgeItemLogRepository;

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

    /**
     * 조립된 냉장고 재료 엔티티 목록을 단일 트랜잭션으로 일괄 저장한다.
     *
     * @param items 저장할 냉장고 재료 엔티티 목록
     * @return 저장된 냉장고 재료 응답 목록
     */
    @Transactional
    public List<FridgeItemResponse> saveItems(List<Fridge> items) {
        return fridgeRepository.saveAll(items)
                .stream()
                .map(FridgeItemResponse::from)
                .toList();
    }

    /**
     * 본인 소유의 냉장고 재료 정보를 더티 체킹으로 수정한다.
     *
     * @param memberId   요청한 회원 ID
     * @param fridgeId   수정할 냉장고 재료 ID
     * @param ingredient 매핑된(또는 기존 유지된) 표준 재료 엔티티
     * @param request    수정할 재료 정보 (rawName, quantity, storage, expirationDate)
     * @return 수정된 냉장고 재료 응답
     */
    @Transactional
    public FridgeItemResponse updateItem(
            Long memberId,
            Long fridgeId,
            Ingredient ingredient,
            FridgeItemUpdateRequest request
    ) {
        Fridge fridge = getFridgeEntity(memberId, fridgeId);
        fridge.update(
                ingredient,
                request.rawName(),
                request.quantity(),
                request.storage(),
                request.expirationDate()
        );
        return FridgeItemResponse.from(fridge);
    }

    /**
     * 본인 소유의 냉장고 재료를 빼서 삭제한다.
     * CONSUMED(다 먹었어요)·DISCARDED(버렸어요)는 삭제 전에 fridge_item_logs에 이력을 먼저 저장하고,
     * WRONG_ENTRY(잘못 등록해서 삭제)는 기록 없이 냉장고에서만 삭제한다.
     * 기록 저장과 재료 삭제는 데이터 정합성을 위해 하나의 트랜잭션 안에서 수행한다.
     *
     * @param memberId 요청한 회원 ID
     * @param fridgeId 삭제할 냉장고 재료 ID
     * @param reason   재료 빼기 사유 (CONSUMED, DISCARDED, WRONG_ENTRY)
     */
    @Transactional
    public void removeItem(Long memberId, Long fridgeId, FridgeRemoveReason reason) {
        Fridge fridge = getFridgeEntity(memberId, fridgeId);

        if (reason.requiresLog()) {
            FridgeItemLog itemLog = FridgeItemLog.builder()
                    .memberId(fridge.getMemberId())
                    .ingredient(fridge.getIngredient())
                    .fridgeId(fridge.getId())
                    .rawName(fridge.getRawName())
                    .quantity(fridge.getQuantity())
                    .action(reason.toAction())
                    .build();
            fridgeItemLogRepository.save(itemLog);
        }

        fridgeRepository.delete(fridge);
    }

    /**
     * 냉장고 재료 ID와 회원 ID로 본인 소유의 재료 엔티티를 조회한다.
     * 존재하지 않거나 다른 회원의 재료이면 타인의 재료 존재 여부를 노출하지 않기 위해 404(FRIDGE_ITEM_NOT_FOUND)로 통일한다.
     *
     * @param memberId 요청한 회원 ID
     * @param fridgeId 조회할 냉장고 재료 ID
     * @return 조회된 냉장고 재료 엔티티 (ingredient 함께 로딩)
     */
    public Fridge getFridgeEntity(Long memberId, Long fridgeId) {
        return fridgeRepository.findByIdAndMemberId(fridgeId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FRIDGE_ITEM_NOT_FOUND));
    }

}
