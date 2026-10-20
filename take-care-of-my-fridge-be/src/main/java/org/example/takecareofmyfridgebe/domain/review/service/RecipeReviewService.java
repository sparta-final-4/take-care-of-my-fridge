package org.example.takecareofmyfridgebe.domain.review.service;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.member.repository.MemberRepository;
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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecipeReviewService {

    private final RecipeReviewRepository recipeReviewRepository;
    private final MemberRepository memberRepository;

    /**
     * 레시피 후기 목록을 검색 조건(recipeId, mine, sort, page, size)으로 조회한다.
     * mine=true인 경우 로그인이 필수이며, 비로그인 요청 시 401(UNAUTHORIZED) 예외를 발생시킨다.
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
        return PageResponse.of(reviews.map(review -> ReviewResponse.from(review, currentMemberId)));
    }

    /**
     * 레시피 후기를 작성하여 저장한다.
     * 회원당 동일 레시피에는 1개의 후기만 작성할 수 있으며, 이미 존재하는 경우 409(REVIEW_ALREADY_EXISTS) 예외를 발생시킨다.
     * 동시 요청으로 인한 DB 유니크 제약 위반(DataIntegrityViolationException) 발생 시에도 409로 변환한다.
     *
     * @param memberId 작성자 회원 ID
     * @param request  후기 생성 정보 (recipeId, rating, content)
     * @return 작성된 후기 상세 응답
     */
    @Transactional
    public ReviewResponse createReview(Long memberId, ReviewCreateRequest request) {
        // TODO: 레시피 도메인에 RecipeRepository/Service가 구현되면 레시피 존재 여부 검증 추가

        if (recipeReviewRepository.existsByMemberIdAndRecipeId(memberId, request.recipeId())) {
            throw new BusinessException(ErrorCode.REVIEW_ALREADY_EXISTS);
        }

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        RecipeReview review = request.toEntity(member);

        try {
            RecipeReview savedReview = recipeReviewRepository.save(review);
            return ReviewResponse.from(savedReview, memberId);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.REVIEW_ALREADY_EXISTS);
        }
    }

    /**
     * 본인이 작성한 레시피 후기를 수정한다.
     * 타인의 후기이거나 존재하지 않는 후기인 경우 보안을 위해 404(REVIEW_NOT_FOUND)로 처리한다.
     *
     * @param memberId 수정 요청한 회원 ID
     * @param reviewId 수정할 후기 ID
     * @param request  수정할 후기 정보 (rating, content)
     * @return 수정된 후기 상세 응답
     */
    @Transactional
    public ReviewResponse updateReview(Long memberId, Long reviewId, ReviewUpdateRequest request) {
        RecipeReview review = getReviewEntity(memberId, reviewId);
        review.update(request.rating(), request.content());
        return ReviewResponse.from(review, memberId);
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
}
