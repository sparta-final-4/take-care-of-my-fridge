package org.example.takecareofmyfridgebe.domain.review.facade;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.cooking.service.CookingService;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.member.service.MemberService;
import org.example.takecareofmyfridgebe.domain.recipe.service.RecipeService;
import org.example.takecareofmyfridgebe.domain.review.dto.ReviewCreateRequest;
import org.example.takecareofmyfridgebe.domain.review.dto.ReviewResponse;
import org.example.takecareofmyfridgebe.domain.review.service.RecipeReviewService;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 후기 작성처럼 레시피·요리 기록·회원 도메인을 함께 확인하는 흐름을 묶는다. 후기 자체의 일은 RecipeReviewService가 한다.
 */
@Component
@RequiredArgsConstructor
public class ReviewFacade {

    private final RecipeService recipeService;
    private final CookingService cookingService;
    private final MemberService memberService;
    private final RecipeReviewService recipeReviewService;

    /**
     * 레시피 후기를 작성하여 저장한다. 확인 순서: 레시피 → 요리 완료 기록 → 중복 후기 → 회원 → 저장 (한 트랜잭션).
     * 레시피가 존재하지 않으면 404(RECIPE_NOT_FOUND) 예외를 발생시킨다.
     * 요리 완료 기록이 없는 레시피면 403(REVIEW_NOT_COOKED) 예외를 발생시킨다.
     * 회원당 동일 레시피에는 1개의 후기만 작성할 수 있으며, 이미 존재하는 경우 409(REVIEW_ALREADY_EXISTS) 예외를 발생시킨다.
     * 동시 작성으로 인한 DB 유니크 제약 위반도 409로 변환한다 (RecipeReviewService.createReview).
     *
     * @param memberId 작성자 회원 ID
     * @param request  후기 생성 정보 (recipeId, rating, content)
     * @return 작성된 후기 상세 응답 (recipeName 포함)
     */
    @Transactional
    public ReviewResponse createReview(Long memberId, ReviewCreateRequest request) {
        if (!recipeService.existsById(request.recipeId())) {
            throw new BusinessException(ErrorCode.RECIPE_NOT_FOUND);
        }

        if (!cookingService.hasCooked(memberId, request.recipeId())) {
            throw new BusinessException(ErrorCode.REVIEW_NOT_COOKED);
        }

        recipeReviewService.validateNotReviewed(memberId, request.recipeId());
        Member member = memberService.getMember(memberId);
        return recipeReviewService.createReview(memberId, member, request);
    }
}
