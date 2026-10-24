package org.example.takecareofmyfridgebe.domain.review.service;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.cooking.service.CookingService;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.member.repository.MemberRepository;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeSummary;
import org.example.takecareofmyfridgebe.domain.recipe.service.RecipeService;
import org.example.takecareofmyfridgebe.domain.review.dto.ReviewCreateRequest;
import org.example.takecareofmyfridgebe.domain.review.dto.ReviewResponse;
import org.example.takecareofmyfridgebe.domain.review.dto.ReviewSearchRequest;
import org.example.takecareofmyfridgebe.domain.review.dto.ReviewUpdateRequest;
import org.example.takecareofmyfridgebe.domain.review.entity.RecipeReview;
import org.example.takecareofmyfridgebe.domain.review.repository.RecipeReviewRepository;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.example.takecareofmyfridgebe.global.response.PageResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecipeReviewService {

    private final RecipeReviewRepository recipeReviewRepository;
    private final MemberRepository memberRepository;
    private final RecipeService recipeService;
    private final CookingService cookingService;

    /**
     * 레시피 후기 목록을 검색 조건(recipeId, mine, sort, page, size)으로 조회한다.
     * mine=true인 경우 로그인이 필수이며, 비로그인 요청 시 401(UNAUTHORIZED) 예외를 발생시킨다.
     * 조회된 후기들의 recipeId를 모아 findSummaries로 한 번에 레시피명을 조회해 매핑한다.
     * 후기 목록이 비어 있으면 findSummaries를 호출하지 않고 빈 목록을 반환한다.
     *
     * @param request         조회 필터 및 페이징 조건
     * @param currentMemberId 현재 로그인한 회원의 ID (비로그인 시 null)
     * @return 페이징된 레시피 후기 목록 응답
     */
    public PageResponse<ReviewResponse> getReviews(ReviewSearchRequest request, Long currentMemberId) {
        if (Boolean.TRUE.equals(request.mine()) && currentMemberId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        Page<RecipeReview> reviews = recipeReviewRepository.findReviewsByCondition(request, currentMemberId);
        if (reviews.isEmpty()) {
            return PageResponse.of(reviews.map(review -> ReviewResponse.from(review, currentMemberId, null)));
        }

        List<Long> recipeIds = reviews.getContent().stream()
                .map(RecipeReview::getRecipeId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<Long, RecipeSummary> summaries = recipeService.findSummaries(recipeIds);

        return PageResponse.of(reviews.map(review -> {
            RecipeSummary summary = summaries.get(review.getRecipeId());
            String recipeName = summary != null ? summary.name() : null;
            return ReviewResponse.from(review, currentMemberId, recipeName);
        }));
    }

    /**
     * 레시피 후기를 작성하여 저장한다.
     * 레시피가 존재하지 않으면 404(RECIPE_NOT_FOUND) 예외를 발생시킨다.
     * 요리 완료 기록이 없는 레시피면 403(REVIEW_NOT_COOKED) 예외를 발생시킨다.
     * 회원당 동일 레시피에는 1개의 후기만 작성할 수 있으며, 이미 존재하는 경우 409(REVIEW_ALREADY_EXISTS) 예외를 발생시킨다.
     * 동시 작성으로 인한 DB 유니크 제약 위반(DataIntegrityViolationException) 발생 시에도 409로 변환한다.
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

        if (recipeReviewRepository.existsByMemberIdAndRecipeId(memberId, request.recipeId())) {
            throw new BusinessException(ErrorCode.REVIEW_ALREADY_EXISTS);
        }

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        RecipeReview review = request.toEntity(member);

        try {
            RecipeReview savedReview = recipeReviewRepository.save(review);
            String recipeName = getRecipeName(savedReview.getRecipeId());
            return ReviewResponse.from(savedReview, memberId, recipeName);
        } catch (DataIntegrityViolationException e) {
            String message = e.getMostSpecificCause().getMessage();
            if (message != null && message.toLowerCase().contains("uk_recipe_reviews_member_id_recipe_id")) {
                throw new BusinessException(ErrorCode.REVIEW_ALREADY_EXISTS);
            }
            throw e;
        }
    }

    /**
     * 본인이 작성한 레시피 후기를 수정한다.
     * 타인의 후기이거나 존재하지 않는 후기인 경우 보안을 위해 404(REVIEW_NOT_FOUND)로 처리한다.
     *
     * @param memberId 수정 요청한 회원 ID
     * @param reviewId 수정할 후기 ID
     * @param request  수정할 후기 정보 (rating, content)
     * @return 수정된 후기 상세 응답 (recipeName 포함)
     */
    @Transactional
    public ReviewResponse updateReview(Long memberId, Long reviewId, ReviewUpdateRequest request) {
        RecipeReview review = getReviewEntity(memberId, reviewId);
        review.update(request.rating(), request.content());
        String recipeName = getRecipeName(review.getRecipeId());
        return ReviewResponse.from(review, memberId, recipeName);
    }

    /**
     * 본인이 작성한 레시피 후기를 삭제한다.
     * 타인의 후기이거나 존재하지 않는 후기인 경우 404(REVIEW_NOT_FOUND)로 처리한다.
     *
     * @param memberId 삭제 요청한 회원 ID
     * @param reviewId 삭제할 후기 ID
     */
    @Transactional
    public void deleteReview(Long memberId, Long reviewId) {
        RecipeReview review = getReviewEntity(memberId, reviewId);
        recipeReviewRepository.delete(review);
    }

    private RecipeReview getReviewEntity(Long memberId, Long reviewId) {
        return recipeReviewRepository.findByIdAndMemberId(reviewId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));
    }

    private String getRecipeName(Long recipeId) {
        if (recipeId == null) {
            return null;
        }
        Map<Long, RecipeSummary> summaries = recipeService.findSummaries(List.of(recipeId));
        RecipeSummary summary = summaries.get(recipeId);
        return summary != null ? summary.name() : null;
    }
}
