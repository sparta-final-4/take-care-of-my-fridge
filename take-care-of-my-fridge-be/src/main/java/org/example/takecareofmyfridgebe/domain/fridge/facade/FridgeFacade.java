package org.example.takecareofmyfridgebe.domain.fridge.facade;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeItemCreateRequest;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeItemResponse;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeItemUpdateRequest;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeNameInfo;
import org.example.takecareofmyfridgebe.domain.fridge.entity.Fridge;
import org.example.takecareofmyfridgebe.domain.fridge.service.FridgeService;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.Ingredient;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;
import org.example.takecareofmyfridgebe.domain.ingredient.service.IngredientMatcher;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class FridgeFacade {

    private final IngredientMatcher ingredientMatcher;
    private final FridgeService fridgeService;

    /**
     * [냉장고 재료 매핑 및 등록]
     * ingredient 도메인(표준 재료 매핑)과 fridge 도메인(냉장고 저장)을 조율한다.
     * 추후 Elasticsearch 외부 I/O가 추가되는 매핑 단계를 DB 트랜잭션 밖에 두기 위해
     * Facade에는 트랜잭션을 걸지 않고, 엔티티 조립 후 FridgeService.saveItems에서 한 트랜잭션으로 저장한다.
     *
     * @param memberId 로그인한 회원 ID
     * @param request  등록할 냉장고 재료 요청 목록
     * @return 등록된 냉장고 재료 응답 목록
     */
    public List<FridgeItemResponse> createItems(Long memberId, FridgeItemCreateRequest request) {
        LocalDate today = LocalDate.now();

        // 등록 항목 수와 관계없이 재료 조회 쿼리를 일괄 처리하기 위해 한 번에 매핑
        Map<String, Ingredient> ingredients = ingredientMatcher.matchAll(
                request.items().stream()
                        .map(FridgeItemCreateRequest.Item::rawName)
                        .toList()
        );

        List<Fridge> fridges = request.items().stream()
                .map(item -> {
                    Ingredient ingredient = ingredients.get(item.rawName());
                    StorageType storage = item.storage() != null
                            ? item.storage()
                            : ingredient.getDefaultStorage();
                    LocalDate expirationDate = item.expirationDate() != null
                            ? item.expirationDate()
                            : ingredient.calculateExpirationDate(today);
                    return item.toEntity(memberId, ingredient, storage, expirationDate);
                })
                .toList();

        return fridgeService.saveItems(fridges);
    }

    /**
     * [냉장고 재료 수정 및 선택적 재매핑]
     * 본인 재료 여부를 먼저 확인한 뒤, 입력된 rawName이 기존과 다를 때만 표준 재료를 다시 매핑한다.
     * 단, 밀키트(ING_MEALKIT) 재료는 이름을 바꿔도 다시 매핑하지 않고 밀키트로 유지한다.
     * 추후 Elasticsearch 외부 I/O가 추가되는 매핑 단계를 DB 쓰기 트랜잭션 밖에 두기 위해
     * Facade에는 트랜잭션을 걸지 않고, 매핑 확정 후 FridgeService.updateItem에서 수정한다.
     *
     * @param memberId 로그인한 회원 ID
     * @param fridgeId 수정할 냉장고 재료 ID
     * @param request  수정할 냉장고 재료 요청 정보
     * @return 수정된 냉장고 재료 응답
     */
    public FridgeItemResponse updateItem(Long memberId, Long fridgeId, FridgeItemUpdateRequest request) {
        // 이름 변경 여부만 확인하는 비교용 조회 (재매핑을 트랜잭션 밖에서 하기 위해 분리)
        FridgeNameInfo current = fridgeService.getNameInfo(memberId, fridgeId);

        // 밀키트이거나, 이름을 안 보냈거나, 같은 이름이면 null을 넘겨 FridgeService.updateItem에서 기존 재료를 유지한다.
        boolean keepIngredient = current.mealkit()
                || request.rawName() == null
                || current.rawName().equals(request.rawName());
        Ingredient ingredient = keepIngredient ? null : ingredientMatcher.match(request.rawName());

        return fridgeService.updateItem(memberId, fridgeId, ingredient, request);
    }
}
