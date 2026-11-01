package org.example.takecareofmyfridgebe.domain.fridge.service;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.cooking.dto.IngredientDeductionResponse;
import org.example.takecareofmyfridgebe.domain.cooking.dto.UsedIngredientRequest;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeItemLogResponse;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeItemLogSearchRequest;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeItemResponse;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeItemTakeOutRequest;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeItemTakeOutResponse;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeItemUpdateRequest;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeNameInfo;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeRemoveReason;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeSortType;
import org.example.takecareofmyfridgebe.domain.fridge.entity.Fridge;
import org.example.takecareofmyfridgebe.domain.fridge.entity.FridgeCommerceHistory;
import org.example.takecareofmyfridgebe.domain.fridge.entity.FridgeItemAction;
import org.example.takecareofmyfridgebe.domain.fridge.entity.FridgeItemLog;
import org.example.takecareofmyfridgebe.domain.fridge.repository.FridgeCommerceHistoryRepository;
import org.example.takecareofmyfridgebe.domain.fridge.repository.FridgeItemLogRepository;
import org.example.takecareofmyfridgebe.domain.fridge.repository.FridgeRepository;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.Ingredient;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;
import org.example.takecareofmyfridgebe.domain.ingredient.service.IngredientService;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.example.takecareofmyfridgebe.global.response.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FridgeService {

    private final FridgeRepository fridgeRepository;
    private final FridgeItemLogRepository fridgeItemLogRepository;
    private final FridgeCommerceHistoryRepository fridgeCommerceHistoryRepository;
    private final IngredientService ingredientService;

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
                .map(fridge -> FridgeItemResponse.from(fridge, today))
                .toList();
    }

    /**
     * 로그인한 회원의 냉장고 재료 기록(먹었어요·버렸어요)을 처리 방식과 기간 조건으로 최신순 페이지 조회한다.
     *
     * @param memberId 조회 대상 회원 ID
     * @param request  조회 조건 (action, from, to, page는 1부터, size)
     * @return 조건에 맞는 기록 페이지
     */
    public PageResponse<FridgeItemLogResponse> getLogs(Long memberId, FridgeItemLogSearchRequest request) {
        if (request.from() != null && request.to() != null && request.from().isAfter(request.to())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        Page<FridgeItemLog> logs = fridgeItemLogRepository.findLogsByCondition(
                memberId,
                request.action(),
                request.from(),
                request.to(),
                PageRequest.of(request.page() - 1, request.size())
        );
        return PageResponse.of(logs.map(FridgeItemLogResponse::from));
    }

    /**
     * 조립된 냉장고 재료 엔티티 목록을 단일 트랜잭션으로 일괄 저장한다.
     *
     * @param items 저장할 냉장고 재료 엔티티 목록
     * @return 저장된 냉장고 재료 응답 목록
     */
    @Transactional
    public List<FridgeItemResponse> saveItems(List<Fridge> items) {
        LocalDate today = LocalDate.now();
        return fridgeRepository.saveAll(items)
                .stream()
                .map(fridge -> FridgeItemResponse.from(fridge, today))
                .toList();
    }

    /**
     * 본인 소유의 냉장고 재료 정보를 더티 체킹으로 부분 수정한다. 요청에서 null인 칸은 바꾸지 않는다.
     * 차감·소비와 겹쳐 수량을 덮어쓰지 않도록 잠금 조회하며, 기다리는 사이 재료가 지워졌으면 404가 된다.
     * 모든 칸이 null이면 바뀌는 것 없이 현재 값을 돌려준다.
     *
     * @param memberId   요청한 회원 ID
     * @param fridgeId   수정할 냉장고 재료 ID
     * @param ingredient 새로 매핑된 표준 재료 엔티티 (null이면 기존 재료 유지)
     * @param request    수정할 재료 정보 (rawName, quantity, storage, expirationDate, 모두 선택)
     * @return 수정된 냉장고 재료 응답
     */
    @Transactional
    public FridgeItemResponse updateItem(
            Long memberId,
            Long fridgeId,
            Ingredient ingredient,
            FridgeItemUpdateRequest request
    ) {
        Fridge fridge = getFridgeEntityForUpdate(memberId, fridgeId);
        fridge.update(
                ingredient,
                request.rawName(),
                request.quantity(),
                request.storage(),
                request.expirationDate()
        );
        return FridgeItemResponse.from(fridge, LocalDate.now());
    }

    /**
     * 주문 상품 ID 중 이미 냉장고에 입고된 것을 조회한다. 같은 구매 확정 메시지를 다시 받았을 때 걸러 내는 데 쓴다.
     *
     * @param orderItemIds 확인할 주문 상품 ID 컬렉션
     * @return 입고 이력이 있는 주문 상품 ID Set
     */
    public Set<Long> getStockedOrderItemIds(Collection<Long> orderItemIds) {
        if (orderItemIds.isEmpty()) {
            return Set.of();
        }
        return fridgeCommerceHistoryRepository.findByOrderItemIdIn(orderItemIds).stream()
                .map(FridgeCommerceHistory::getOrderItemId)
                .collect(Collectors.toSet());
    }

    /**
     * 구매 확정된 주문 상품을 냉장고에 넣고 주문 상품마다 입고 이력을 남긴다.
     * 주문 한 건을 한 트랜잭션으로 저장해 일부만 들어가지 않게 한다.
     * 이력의 주문 상품 유니크 제약이 중복 입고의 마지막 방어선이며, 이력을 먼저 저장해 중복이면 냉장고 행을 넣기 전에 실패한다.
     *
     * @param items 입고할 냉장고 재료 엔티티 목록 (source=COMMERCE, orderItemId 포함)
     */
    @Transactional
    public void stockCommerceItems(List<Fridge> items) {
        fridgeCommerceHistoryRepository.saveAll(items.stream()
                .map(item -> FridgeCommerceHistory.builder()
                        .memberId(item.getMemberId())
                        .orderItemId(item.getOrderItemId())
                        .build())
                .toList());
        fridgeRepository.saveAll(items);
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
        // 같은 재료를 동시에 사용·삭제할 때 수량을 덮어쓰거나 이미 지워진 재료를 다시 지우지 않도록 잠금
        Fridge fridge = getFridgeEntityForUpdate(memberId, fridgeId);

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
     * 본인 소유의 냉장고 재료를 일부 또는 전부 먹었거나 버린 것으로 처리한다.
     * 요청 수량이 없거나 남은 양 이상이면 전부 뺀 것으로 보고, 차감 후 남은 양이 0이 되면 냉장고에서 삭제한다.
     * 실제로 뺀 양을 fridge_item_logs에 기록하며, 기록 저장과 차감·삭제는 하나의 트랜잭션 안에서 수행한다.
     *
     * @param memberId 요청한 회원 ID
     * @param fridgeId 처리할 냉장고 재료 ID
     * @param request  처리 방식(CONSUMED, DISCARDED)과 뺄 수량(null이면 전부)
     * @return 삭제 여부와 남은 양
     */
    @Transactional
    public FridgeItemTakeOutResponse takeOutItem(Long memberId, Long fridgeId, FridgeItemTakeOutRequest request) {
        // 같은 재료를 동시에 사용할 때 수량을 덮어쓰지 않도록 잠금.
        // 뒤 요청은 앞 요청이 커밋될 때까지 기다린 뒤 남은 양 기준으로 처리되고, 앞 요청이 지웠으면 404가 된다.
        Fridge fridge = getFridgeEntityForUpdate(memberId, fridgeId);
        // 남은 양보다 큰 수량은 전부 빼기로 바뀌므로 차감 전에 요청 수량으로 검사한다.
        fridge.validateMealkitQuantity(request.quantity());
        deductAndLog(fridge, request.quantity(), request.action(), null);
        return FridgeItemTakeOutResponse.from(fridge);
    }

    /**
     * 요리 완료 시 필요한 냉장고 재료들을 소비기한 빠른 순으로 차감한다.
     * 여러 재료를 잠글 때의 교착(Deadlock) 방지를 위해 ingredientId 오름차순으로 처리한다.
     * 기본 양념, 직접 입력 재료(ING_CUSTOM), 밀키트(ING_MEALKIT)는 차감 대상에서 제외한다.
     * 냉장고에 보유하지 않은 재료는 에러 없이 건너뛰며, 차감된 재료만 결과 목록에 포함된다.
     *
     * @param memberId        회원 ID
     * @param cookingLogId    생성된 요리 기록 ID
     * @param usedIngredients 차감 요청 재료 목록
     * @return 차감된 재료별 차감량 및 잔여량 응답 목록
     */
    @Transactional
    public List<IngredientDeductionResponse> consumeForCooking(
            Long memberId,
            Long cookingLogId,
            List<UsedIngredientRequest> usedIngredients
    ) {
        if (usedIngredients == null || usedIngredients.isEmpty()) {
            return List.of();
        }

        List<Long> requestedIds = usedIngredients.stream()
                .map(UsedIngredientRequest::ingredientId)
                .toList();
        Set<Long> excludedIds = ingredientService.getExcludedIngredientIds(requestedIds);

        // 교착 방지를 위해 ingredientId 오름차순으로 정렬하여 잠금 획득
        List<UsedIngredientRequest> sortedRequests = usedIngredients.stream()
                .filter(item -> !excludedIds.contains(item.ingredientId()))
                .sorted(Comparator.comparing(UsedIngredientRequest::ingredientId))
                .toList();

        List<IngredientDeductionResponse> deductions = new ArrayList<>();

        for (UsedIngredientRequest request : sortedRequests) {
            Long ingredientId = request.ingredientId();
            List<Fridge> items = fridgeRepository.findForUpdateByMemberIdAndIngredientId(memberId, ingredientId);
            if (items.isEmpty()) {
                continue;
            }

            BigDecimal totalAvailable = items.stream()
                    .map(Fridge::getQuantity)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal needed = request.quantity();
            boolean takeAll = needed == null || needed.compareTo(totalAvailable) >= 0;
            BigDecimal remainingToDeduct = takeAll ? totalAvailable : needed;

            BigDecimal actuallyDeducted = BigDecimal.ZERO;

            for (Fridge item : items) {
                if (remainingToDeduct.compareTo(BigDecimal.ZERO) <= 0) {
                    break;
                }
                BigDecimal before = item.getQuantity();
                BigDecimal deductForThisItem = remainingToDeduct.compareTo(before) >= 0 ? before : remainingToDeduct;

                BigDecimal deductedFromItem = deductAndLog(item, deductForThisItem, FridgeItemAction.CONSUMED, cookingLogId);
                actuallyDeducted = actuallyDeducted.add(deductedFromItem);
                remainingToDeduct = remainingToDeduct.subtract(deductForThisItem);
            }

            if (actuallyDeducted.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal remainingTotal = items.stream()
                        .filter(item -> !item.isEmpty())
                        .map(Fridge::getQuantity)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                deductions.add(new IngredientDeductionResponse(ingredientId, actuallyDeducted, remainingTotal));
            }
        }

        return deductions;
    }

    /**
     * 재료 수량을 차감하고, 이력(FridgeItemLog)을 저장하며, 잔여량이 0이 되면 냉장고에서 삭제한다.
     * 차감 후 0.05 미만으로 남아 0으로 보정된 경우도 처리 전 수량 전체를 뺀 것으로 계산한다.
     *
     * @param fridge        차감할 냉장고 재료 엔티티
     * @param quantity      차감 요청 수량 (null이면 전체)
     * @param action        이력 처리 액션 (CONSUMED, DISCARDED)
     * @param cookingLogId  요리 완료 연결 ID (요리 소진이 아닌 경우 null)
     * @return 실제로 차감된 수량
     */
    private BigDecimal deductAndLog(Fridge fridge, BigDecimal quantity, FridgeItemAction action, Long cookingLogId) {
        BigDecimal before = fridge.getQuantity();
        boolean takeAll = quantity == null || quantity.compareTo(before) >= 0;

        fridge.deduct(takeAll ? before : quantity);
        BigDecimal takenQuantity = fridge.isEmpty() ? before : quantity;

        FridgeItemLog itemLog = FridgeItemLog.builder()
                .memberId(fridge.getMemberId())
                .ingredient(fridge.getIngredient())
                .cookingLogId(cookingLogId)
                .fridgeId(fridge.getId())
                .rawName(fridge.getRawName())
                .quantity(takenQuantity)
                .action(action)
                .build();
        fridgeItemLogRepository.save(itemLog);

        if (fridge.isEmpty()) {
            fridgeRepository.delete(fridge);
        }

        return takenQuantity;
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

    /**
     * 냉장고 재료 ID와 회원 ID로 본인 소유의 재료 엔티티를 쓰기 잠금(SELECT ... FOR UPDATE)으로 조회한다.
     * 잠금은 호출한 트랜잭션이 끝날 때까지 유지되며, 기다리는 사이 재료가 지워졌으면 404(FRIDGE_ITEM_NOT_FOUND)로 통일한다.
     *
     * @param memberId 요청한 회원 ID
     * @param fridgeId 조회할 냉장고 재료 ID
     * @return 잠금이 걸린 냉장고 재료 엔티티 (ingredient는 지연 로딩)
     */
    private Fridge getFridgeEntityForUpdate(Long memberId, Long fridgeId) {
        return fridgeRepository.findForUpdateByIdAndMemberId(fridgeId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FRIDGE_ITEM_NOT_FOUND));
    }

    /**
     * 냉장고 재료 ID와 회원 ID로 본인 소유 재료의 rawName과 밀키트 여부만 조회한다.
     * 지연 로딩되는 ingredient를 트랜잭션 안에서 확인해 Facade로 엔티티를 넘기지 않는다.
     * 존재하지 않거나 다른 회원의 재료이면 404(FRIDGE_ITEM_NOT_FOUND)로 통일한다.
     *
     * @param memberId 요청한 회원 ID
     * @param fridgeId 조회할 냉장고 재료 ID
     * @return 저장된 재료 원문 이름(rawName)과 밀키트 여부
     */
    public FridgeNameInfo getNameInfo(Long memberId, Long fridgeId) {
        Fridge fridge = getFridgeEntity(memberId, fridgeId);
        Ingredient ingredient = fridge.getIngredient();
        return new FridgeNameInfo(fridge.getRawName(), ingredient != null && ingredient.isMealkit());
    }

    /**
     * 요리 완료로 소진한 재료 종류 수를 조회한다.
     *
     * @param memberId 회원 ID
     * @return 요리 완료로 소진한 재료 종류 수
     */
    public long countConsumedByCooking(Long memberId) {
        if (memberId == null) {
            return 0L;
        }
        return fridgeItemLogRepository.countConsumedByCooking(memberId);
    }
}
