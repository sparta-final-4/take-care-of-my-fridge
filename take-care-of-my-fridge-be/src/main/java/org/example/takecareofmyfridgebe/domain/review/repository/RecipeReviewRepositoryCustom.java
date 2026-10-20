package org.example.takecareofmyfridgebe.domain.review.repository;

import org.example.takecareofmyfridgebe.domain.review.dto.ReviewSearchRequest;
import org.example.takecareofmyfridgebe.domain.review.entity.RecipeReview;
import org.springframework.data.domain.Page;

public interface RecipeReviewRepositoryCustom {

    /**
     * 레시피 ID, 본인 작성 여부(mine), 정렬 조건에 따라 후기 목록을 페이징 조회한다.
     * 작성자 닉네임 조회를 위해 Member를 fetch join한다.
     *
     * @param request         검색 및 페이징 조건 (recipeId, mine, sort, page, size)
     * @param currentMemberId 현재 로그인한 회원 ID (mine=true 조건 필터링에 사용)
     * @return 페이징된 레시피 후기 엔티티 목록
     */
    Page<RecipeReview> findReviewsByCondition(ReviewSearchRequest request, Long currentMemberId);
}
