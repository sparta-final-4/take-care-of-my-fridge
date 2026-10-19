package org.example.takecareofmyfridgebe.domain.fridge.facade;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeItemCreateRequest;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeItemResponse;
import org.example.takecareofmyfridgebe.domain.fridge.entity.Fridge;
import org.example.takecareofmyfridgebe.domain.fridge.service.FridgeService;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.Ingredient;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;
import org.example.takecareofmyfridgebe.domain.ingredient.service.IngredientMatcher;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

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

        List<Fridge> fridges = request.items().stream()
                .map(item -> {
                    Ingredient ingredient = ingredientMatcher.match(item.rawName());
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
}
