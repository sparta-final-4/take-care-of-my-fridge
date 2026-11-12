package org.example.takecareofmyfridgebe.domain.review.service;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeSummary;
import org.example.takecareofmyfridgebe.domain.recipe.service.RecipeService;
import org.example.takecareofmyfridgebe.domain.review.dto.RecipeRatingStat;
import org.example.takecareofmyfridgebe.domain.review.dto.RecipeReviewContentDto;
import org.example.takecareofmyfridgebe.domain.review.dto.RecipeReviewSummaryCandidate;
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

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecipeReviewService {

    private final RecipeReviewRepository recipeReviewRepository;
    private final RecipeService recipeService;

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

        return PageResponse.of(reviews.map(review ->
                ReviewResponse.from(review, currentMemberId, recipeNameOf(summaries, review.getRecipeId()))));
    }

    /**
     * 회원이 이 레시피에 이미 후기를 썼으면 409(REVIEW_ALREADY_EXISTS) 예외를 발생시킨다 (회원당 레시피 하나에 후기 1개).
     *
     * @param memberId 작성자 회원 ID
     * @param recipeId 레시피 ID
     */
    public void validateNotReviewed(Long memberId, Long recipeId) {
        if (recipeReviewRepository.existsByMemberIdAndRecipeId(memberId, recipeId)) {
            throw new BusinessException(ErrorCode.REVIEW_ALREADY_EXISTS);
        }
    }

    /**
     * 레시피 후기를 저장한다. 레시피·요리 기록·회원 확인은 ReviewFacade.createReview가 먼저 한다.
     * 동시 작성으로 인한 DB 유니크 제약 위반(DataIntegrityViolationException) 발생 시에도 409(REVIEW_ALREADY_EXISTS)로 변환한다.
     *
     * @param memberId 작성자 회원 ID
     * @param member   작성자 회원
     * @param request  후기 생성 정보 (recipeId, rating, content)
     * @return 작성된 후기 상세 응답 (recipeName 포함)
     */
    @Transactional
    public ReviewResponse createReview(Long memberId, Member member, ReviewCreateRequest request) {
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

    // 후기 하나의 레시피 이름. 레시피 ID가 없으면 조회하지 않는다
    private String getRecipeName(Long recipeId) {
        if (recipeId == null) {
            return null;
        }
        return recipeNameOf(recipeService.findSummaries(List.of(recipeId)), recipeId);
    }

    /**
     * 조회한 레시피 요약에서 이름을 꺼낸다. 레시피가 지워져 요약이 없으면 null이고,
     * 응답의 recipeName도 null로 나간다 (이름을 모르는 후기). null을 돌려주는 곳은 여기 하나다.
     */
    private static String recipeNameOf(Map<Long, RecipeSummary> summaries, Long recipeId) {
        RecipeSummary summary = summaries.get(recipeId);
        return summary != null ? summary.name() : null;
    }

    /**
     * 레시피 ID 목록의 별점 통계(평균 평점, 리뷰 수)를 한 번에 조회한다.
     * ID 목록이 비어 있으면 DB 쿼리를 실행하지 않고 빈 맵을 반환한다.
     *
     * @param recipeIds 레시피 ID 목록
     * @return 레시피 ID를 키로 하는 별점 통계 맵
     */
    public Map<Long, RecipeRatingStat> findRatingStats(Collection<Long> recipeIds) {
        if (recipeIds == null || recipeIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Long> distinctIds = recipeIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (distinctIds.isEmpty()) {
            return Collections.emptyMap();
        }

        return recipeReviewRepository.findRatingStatsByRecipeIds(distinctIds).stream()
                .collect(Collectors.toMap(
                        RecipeRatingStat::recipeId,
                        stat -> stat,
                        (existing, replacing) -> existing
                ));
    }

    /**
     * 후기 요약 대상 후보(후기 5개 이상인 레시피별 후기 수와 가장 최근 후기 수정 시각)를 조회한다.
     *
     * @return 요약 대상 후보 목록
     */
    public List<RecipeReviewSummaryCandidate> findSummaryCandidatesWithMinReviews() {
        return recipeReviewRepository.findSummaryCandidatesWithMinReviews();
    }

    /**
     * 특정 레시피의 최근 수정된 후기를 최신순으로 limit 건만큼 별점과 내용만 조회한다.
     *
     * @param recipeId 레시피 ID
     * @param limit    최대 조회 건수
     * @return 후기 별점·내용 목록
     */
    public List<RecipeReviewContentDto> findRecentReviewsByRecipeId(Long recipeId, int limit) {
        return recipeReviewRepository.findRecentReviewsByRecipeId(recipeId, limit);
    }
}
