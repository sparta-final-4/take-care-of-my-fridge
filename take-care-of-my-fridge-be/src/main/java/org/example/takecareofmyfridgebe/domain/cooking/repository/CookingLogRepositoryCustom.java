package org.example.takecareofmyfridgebe.domain.cooking.repository;

import org.example.takecareofmyfridgebe.domain.cooking.dto.CookingStatsResponse;
import org.example.takecareofmyfridgebe.domain.cooking.dto.MostCookedRecipeDto;
import org.example.takecareofmyfridgebe.domain.cooking.dto.MyRecipeResponse;
import org.example.takecareofmyfridgebe.domain.cooking.dto.MyRecipeReviewStatus;
import org.example.takecareofmyfridgebe.domain.cooking.dto.MyRecipeSortType;
import org.example.takecareofmyfridgebe.domain.cooking.dto.MyRecipeSummaryCount;
import org.example.takecareofmyfridgebe.domain.cooking.dto.PopularRecipeCookingCount;
import org.example.takecareofmyfridgebe.domain.cooking.entity.CookingLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CookingLogRepositoryCustom {

    /**
     * 회원의 요리 기록을 레시피 ID 및 기간 조건으로 최신순 페이지 조회한다.
     *
     * @param memberId 회원 ID (필수)
     * @param recipeId 레시피 ID 필터 (선택)
     * @param from     시작일 (선택)
     * @param to       종료일 (선택, 해당 일자 하루 전체 포함)
     * @param pageable 페이징 정보
     * @return 조건에 맞는 요리 기록 페이지
     */
    Page<CookingLog> findLogsByCondition(
            Long memberId,
            Long recipeId,
            LocalDate from,
            LocalDate to,
            Pageable pageable
    );

    /**
     * 회원이 요리 완료한 레시피 목록을 레시피별로 묶어 페이징 조회한다.
     *
     * @param memberId     회원 ID (필수)
     * @param reviewStatus 후기 작성 여부 필터 (ALL, WRITTEN, NOT_WRITTEN)
     * @param sort         정렬 기준 (RECENT, MOST)
     * @param pageable     페이징 정보
     * @return 묶인 레시피 목록 페이지
     */
    Page<MyRecipeResponse> findMyRecipes(
            Long memberId,
            MyRecipeReviewStatus reviewStatus,
            MyRecipeSortType sort,
            Pageable pageable
    );

    /**
     * 회원의 요리 완료 레시피 전체 요약 통계 카운트를 단일 쿼리로 조회한다.
     *
     * @param memberId 회원 ID (필수)
     * @return 레시피 수, 총 요리 횟수, 작성/미작성 후기 수
     */
    MyRecipeSummaryCount getMyRecipesSummaryCount(Long memberId);

    /**
     * 회원이 가장 많이 요리한 레시피의 ID와 요리 횟수를 조회한다.
     * 요리 횟수가 같으면 가장 최근에 요리한 레시피를 우선한다.
     *
     * @param memberId 회원 ID (필수)
     * @return 가장 많이 요리한 레시피 ID 및 횟수 (없으면 empty)
     */
    Optional<MostCookedRecipeDto> findMostCookedRecipe(Long memberId);

    /**
     * 특정 레시피의 요리 통계(총 요리 횟수, 성공 횟수, 실패 횟수)를 단일 쿼리로 실시간 집계한다.
     * 요리 기록이 없는 레시피는 모든 카운트가 0으로 반환된다.
     *
     * @param recipeId 레시피 ID (필수)
     * @return 레시피 요리 통계 응답
     */
    CookingStatsResponse getCookingStats(Long recipeId);

    /**
     * 지정한 시각 이후의 요리 기록을 바탕으로 인기 레시피 집계 결과를 조회한다.
     * 정렬 기준: 만든 회원 수 DESC -> 총 요리 횟수 DESC -> 마지막 요리 최근 순 DESC -> recipe_id ASC
     *
     * @param since 집계 기준 시작 일시 (예: 현재 - 30일)
     * @param size  조회할 최대 레시피 개수
     * @return 인기 레시피 ID 및 만든 회원 수 목록
     */
    List<PopularRecipeCookingCount> findPopularCookingCounts(LocalDateTime since, int size);
}
