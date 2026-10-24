package org.example.takecareofmyfridgebe.domain.cooking.service;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.cooking.dto.CookingCompleteRequest;
import org.example.takecareofmyfridgebe.domain.cooking.dto.CookingLogResponse;
import org.example.takecareofmyfridgebe.domain.cooking.dto.CookingLogSearchRequest;
import org.example.takecareofmyfridgebe.domain.cooking.dto.CookingLogUpdateRequest;
import org.example.takecareofmyfridgebe.domain.cooking.dto.CookingStatsResponse;
import org.example.takecareofmyfridgebe.domain.cooking.dto.MyRecipeListResponse;
import org.example.takecareofmyfridgebe.domain.cooking.dto.MyRecipeResponse;
import org.example.takecareofmyfridgebe.domain.cooking.dto.MyRecipeSearchRequest;
import org.example.takecareofmyfridgebe.domain.cooking.dto.MyRecipeSummaryResponse;
import org.example.takecareofmyfridgebe.domain.cooking.entity.CookingLog;
import org.example.takecareofmyfridgebe.domain.cooking.repository.CookingLogRepository;
import org.example.takecareofmyfridgebe.domain.recipe.service.RecipeService;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.example.takecareofmyfridgebe.global.response.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CookingService {

    private final CookingLogRepository cookingLogRepository;
    private final RecipeService recipeService;

    /**
     * 요리 완료 기록을 생성하여 저장한다.
     *
     * @param memberId 회원 ID
     * @param request  요리 완료 요청 정보
     * @return 저장된 요리 기록 엔티티
     */
    @Transactional
    public CookingLog saveCookingLog(Long memberId, CookingCompleteRequest request) {
        if (!recipeService.existsById(request.recipeId())) {
            throw new BusinessException(ErrorCode.RECIPE_NOT_FOUND);
        }

        CookingLog cookingLog = request.toEntity(memberId);
        return cookingLogRepository.save(cookingLog);
    }

    /**
     * 회원이 특정 레시피를 요리 완료한 기록이 존재하는지 확인한다.
     *
     * @param memberId 회원 ID
     * @param recipeId 레시피 ID
     * @return 요리 완료 기록 존재 여부
     */
    public boolean hasCooked(Long memberId, Long recipeId) {
        return cookingLogRepository.existsByMemberIdAndRecipeId(memberId, recipeId);
    }

    /**
     * 로그인한 회원의 요리 기록을 레시피 및 기간 조건으로 최신순 페이지 조회한다.
     *
     * @param memberId 조회 대상 회원 ID
     * @param request  조회 조건 (recipeId, from, to, page는 1부터, size)
     * @return 조건에 맞는 요리 기록 페이지
     */
    public PageResponse<CookingLogResponse> getCookingLogs(Long memberId, CookingLogSearchRequest request) {
        if (request.from() != null && request.to() != null && request.from().isAfter(request.to())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        Page<CookingLog> logs = cookingLogRepository.findLogsByCondition(
                memberId,
                request.recipeId(),
                request.from(),
                request.to(),
                PageRequest.of(request.page() - 1, request.size())
        );
        return PageResponse.of(logs.map(CookingLogResponse::from));
    }

    /**
     * 로그인한 회원이 요리 완료한 레시피를 레시피별로 묶어 페이징 목록 및 전체 요약 통계를 반환한다.
     *
     * @param memberId 조회 대상 회원 ID
     * @param request  조회 조건 (reviewStatus, sort, page는 1부터, size)
     * @return 요약 통계와 레시피 목록 페이징 응답
     */
    public MyRecipeListResponse getMyRecipes(Long memberId, MyRecipeSearchRequest request) {
        Page<MyRecipeResponse> recipePage = cookingLogRepository.findMyRecipes(
                memberId,
                request.reviewStatus(),
                request.sort(),
                PageRequest.of(request.page() - 1, request.size())
        );
        MyRecipeSummaryResponse summary = cookingLogRepository.getMyRecipesSummary(memberId);

        return new MyRecipeListResponse(summary, PageResponse.of(recipePage));
    }

    /**
     * 로그인한 회원의 요리 기록 결과와 메모를 수정한다.
     * 본인의 기록만 수정할 수 있으며, 다른 회원의 기록이거나 존재하지 않으면 404 예외를 던진다.
     * 재료 차감과 묶인 값은 되돌리기 어려워 결과와 메모만 수정하며 JPA 더티 체킹으로 반영한다.
     *
     * @param memberId     회원 ID
     * @param cookingLogId 요리 기록 ID
     * @param request      수정 정보 (result, memo)
     * @return 수정된 요리 기록 응답
     */
    @Transactional
    public CookingLogResponse updateCookingLog(Long memberId, Long cookingLogId, CookingLogUpdateRequest request) {
        CookingLog cookingLog = cookingLogRepository.findByIdAndMemberId(cookingLogId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COOKING_LOG_NOT_FOUND));

        cookingLog.update(request.result(), request.memo());
        return CookingLogResponse.from(cookingLog);
    }

    /**
     * 특정 레시피의 요리 통계(총 요리 횟수, 성공 횟수, 실패 횟수)를 계산하여 반환한다.
     * 레시피가 없으면 404(RECIPE_NOT_FOUND) 예외를 던지고, 요리 기록이 없는 경우 모두 0인 통계를 반환한다.
     *
     * @param recipeId 레시피 ID
     * @return 레시피 요리 통계 응답
     */
    public CookingStatsResponse getCookingStats(Long recipeId) {
        if (!recipeService.existsById(recipeId)) {
            throw new BusinessException(ErrorCode.RECIPE_NOT_FOUND);
        }
        return cookingLogRepository.getCookingStats(recipeId);
    }
}
