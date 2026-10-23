package org.example.takecareofmyfridgebe.domain.review.service;

import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.member.repository.MemberRepository;
import org.example.takecareofmyfridgebe.domain.recipe.service.RecipeService;
import org.example.takecareofmyfridgebe.domain.review.dto.ReviewCreateRequest;
import org.example.takecareofmyfridgebe.domain.review.dto.ReviewResponse;
import org.example.takecareofmyfridgebe.domain.review.dto.ReviewSearchRequest;
import org.example.takecareofmyfridgebe.domain.review.dto.ReviewSortType;
import org.example.takecareofmyfridgebe.domain.review.dto.ReviewUpdateRequest;
import org.example.takecareofmyfridgebe.domain.review.entity.RecipeReview;
import org.example.takecareofmyfridgebe.domain.review.repository.RecipeReviewRepository;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.example.takecareofmyfridgebe.global.response.PageResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RecipeReviewService 단위 테스트.
 * 후기 조회 조건(mine, sort)과 DTO 변환(mine 여부, 작성자 닉네임),
 * 작성 시 중복 체크 및 동시성 유니크 제약 위반 예외 변환,
 * 수정·삭제 시 본인 작성 여부 검증 및 더티 체킹을 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class RecipeReviewServiceTest {

    @Mock
    private RecipeReviewRepository recipeReviewRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private RecipeService recipeService;

    @InjectMocks
    private RecipeReviewService recipeReviewService;

    private Member createMember(Long id, String nickname) {
        Member member = Member.builder().email("user" + id + "@example.com").passwordHash("encoded_password").nickname(nickname).tastes(Collections.emptySet()).build();
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private RecipeReview createReview(Long id, Member member, Long recipeId, int rating, String content) {
        RecipeReview review = RecipeReview.builder()
                .member(member)
                .recipeId(recipeId)
                .rating(rating)
                .content(content)
                .build();
        ReflectionTestUtils.setField(review, "id", id);
        ReflectionTestUtils.setField(review, "createdAt", LocalDateTime.of(2026, 10, 20, 12, 0));
        ReflectionTestUtils.setField(review, "updatedAt", LocalDateTime.of(2026, 10, 20, 12, 0));
        return review;
    }

    /**
     * mine=true이면서 비로그인 상태(currentMemberId == null)로 조회 요청 시
     * 401 UNAUTHORIZED 예외가 발생하는지 검증한다.
     */
    @Test
    void getReviewsThrowsUnauthorizedWhenMineRequestedWithoutLogin() {
        // Given
        ReviewSearchRequest request = new ReviewSearchRequest(1L, true, ReviewSortType.LATEST, 1, 20);

        // When & Then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> recipeReviewService.getReviews(request, null)
        );
        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
    }

    /**
     * 정상적인 후기 목록 조회 시 페이징 응답 및 DTO의 mine 플래그가 정상 설정되는지 검증한다.
     */
    @Test
    void getReviewsReturnsPagedResponseWithMineFlag() {
        // Given
        Long currentMemberId = 1L;
        Member member1 = createMember(1L, "사용자1");
        Member member2 = createMember(2L, "사용자2");

        RecipeReview review1 = createReview(101L, member1, 10L, 5, "최고예요!");
        RecipeReview review2 = createReview(102L, member2, 10L, 4, "좋아요");

        ReviewSearchRequest request = new ReviewSearchRequest(10L, false, ReviewSortType.LATEST, 1, 20);
        Page<RecipeReview> reviewPage = new PageImpl<>(List.of(review1, review2));

        when(recipeReviewRepository.findReviewsByCondition(request, currentMemberId)).thenReturn(reviewPage);

        // When
        PageResponse<ReviewResponse> response = recipeReviewService.getReviews(request, currentMemberId);

        // Then
        assertEquals(2, response.content().size());
        assertEquals(101L, response.content().get(0).id());
        assertTrue(response.content().get(0).mine());
        assertEquals("사용자1", response.content().get(0).nickname());

        assertEquals(102L, response.content().get(1).id());
        assertFalse(response.content().get(1).mine());
        assertEquals("사용자2", response.content().get(1).nickname());
    }

    /**
     * 후기 작성 시 레시피가 존재하지 않으면 404 RECIPE_NOT_FOUND 예외가 발생하는지 검증한다.
     */
    @Test
    void createReviewThrowsRecipeNotFoundWhenRecipeDoesNotExist() {
        // Given
        Long memberId = 1L;
        ReviewCreateRequest request = new ReviewCreateRequest(999L, 5, "후기 내용");
        when(recipeService.existsById(999L)).thenReturn(false);

        // When & Then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> recipeReviewService.createReview(memberId, request)
        );
        assertEquals(ErrorCode.RECIPE_NOT_FOUND, exception.getErrorCode());
        verify(recipeReviewRepository, never()).save(any(RecipeReview.class));
    }

    /**
     * 정상적인 후기 작성 요청 시 엔티티를 저장하고 작성자의 후기 DTO(mine=true)를 반환하는지 검증한다.
     */
    @Test
    void createReviewSavesReviewAndReturnsResponse() {
        // Given: 1번 회원의 10번 레시피 후기 작성 요청
        Long memberId = 1L;
        Member member = createMember(memberId, "사용자1");
        ReviewCreateRequest request = new ReviewCreateRequest(10L, 5, "정말 맛있어요!");

        when(recipeService.existsById(10L)).thenReturn(true);
        when(recipeReviewRepository.existsByMemberIdAndRecipeId(memberId, 10L)).thenReturn(false);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));

        RecipeReview savedReview = createReview(100L, member, 10L, 5, "정말 맛있어요!");
        when(recipeReviewRepository.save(any(RecipeReview.class))).thenReturn(savedReview);

        // When
        ReviewResponse response = recipeReviewService.createReview(memberId, request);

        // Then
        assertEquals(100L, response.id());
        assertEquals(5, response.rating());
        assertEquals("정말 맛있어요!", response.content());
        assertTrue(response.mine());
        verify(recipeReviewRepository).save(any(RecipeReview.class));
    }

    /**
     * 후기 작성 시 회원이 존재하지 않으면 404 MEMBER_NOT_FOUND 예외가 발생하는지 검증한다.
     */
    @Test
    void createReviewThrowsMemberNotFoundWhenMemberDoesNotExist() {
        // Given
        Long memberId = 999L;
        ReviewCreateRequest request = new ReviewCreateRequest(10L, 5, "후기 내용");

        when(recipeService.existsById(10L)).thenReturn(true);
        when(recipeReviewRepository.existsByMemberIdAndRecipeId(memberId, 10L)).thenReturn(false);
        when(memberRepository.findById(memberId)).thenReturn(Optional.empty());

        // When & Then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> recipeReviewService.createReview(memberId, request)
        );
        assertEquals(ErrorCode.MEMBER_NOT_FOUND, exception.getErrorCode());
        verify(recipeReviewRepository, never()).save(any(RecipeReview.class));
    }

    /**
     * 이미 동일 레시피에 후기를 작성한 회원이 중복 작성을 시도하면
     * 사전 조회에서 409 REVIEW_ALREADY_EXISTS 예외가 발생하고 저장을 호출하지 않는지 검증한다.
     */
    @Test
    void createReviewThrowsConflictWhenAlreadyExists() {
        // Given: 이미 후기가 존재하는 레시피
        Long memberId = 1L;
        ReviewCreateRequest request = new ReviewCreateRequest(10L, 5, "중복 후기");

        when(recipeService.existsById(10L)).thenReturn(true);
        when(recipeReviewRepository.existsByMemberIdAndRecipeId(memberId, 10L)).thenReturn(true);

        // When & Then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> recipeReviewService.createReview(memberId, request)
        );
        assertEquals(ErrorCode.REVIEW_ALREADY_EXISTS, exception.getErrorCode());
        verify(recipeReviewRepository, never()).save(any(RecipeReview.class));
    }

    /**
     * 동시 요청으로 인해 DB 유니크 제약 위반(DataIntegrityViolationException)이 발생하면
     * 409 REVIEW_ALREADY_EXISTS 예외로 변환하여 던지는지 검증한다.
     */
    @Test
    void createReviewConvertsDataIntegrityViolationExceptionToConflict() {
        // Given: 사전 조회는 통과했으나 save 시점에 DB 유니크 제약 위반 발생
        Long memberId = 1L;
        Member member = createMember(memberId, "사용자1");
        ReviewCreateRequest request = new ReviewCreateRequest(10L, 5, "동시 후기");

        when(recipeService.existsById(10L)).thenReturn(true);
        when(recipeReviewRepository.existsByMemberIdAndRecipeId(memberId, 10L)).thenReturn(false);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(recipeReviewRepository.save(any(RecipeReview.class)))
                .thenThrow(new DataIntegrityViolationException("Unique constraint violated"));

        // When & Then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> recipeReviewService.createReview(memberId, request)
        );
        assertEquals(ErrorCode.REVIEW_ALREADY_EXISTS, exception.getErrorCode());
    }

    /**
     * 본인의 후기를 수정할 때 엔티티의 별점과 내용이 더티 체킹으로 변경되는지 검증한다.
     */
    @Test
    void updateReviewModifiesEntityViaDirtyChecking() {
        // Given: 1번 회원의 100번 후기
        Long memberId = 1L;
        Long reviewId = 100L;
        Member member = createMember(memberId, "사용자1");
        RecipeReview review = createReview(reviewId, member, 10L, 3, "보통이에요");

        ReviewUpdateRequest updateRequest = new ReviewUpdateRequest(5, "다시 해보니 정말 맛있네요!");
        when(recipeReviewRepository.findByIdAndMemberId(reviewId, memberId)).thenReturn(Optional.of(review));

        // When
        ReviewResponse response = recipeReviewService.updateReview(memberId, reviewId, updateRequest);

        // Then
        assertEquals(5, response.rating());
        assertEquals("다시 해보니 정말 맛있네요!", response.content());
        assertEquals(5, review.getRating());
        assertEquals("다시 해보니 정말 맛있네요!", review.getContent());
    }

    /**
     * 타인의 후기이거나 존재하지 않는 후기 수정 시 404 REVIEW_NOT_FOUND 예외가 발생하는지 검증한다.
     */
    @Test
    void updateReviewThrowsNotFoundWhenOwnedByAnotherMemberOrMissing() {
        // Given: 존재하지 않거나 타인의 후기 조회 결과 empty
        Long memberId = 1L;
        Long reviewId = 999L;
        ReviewUpdateRequest updateRequest = new ReviewUpdateRequest(5, "수정 내용");

        when(recipeReviewRepository.findByIdAndMemberId(reviewId, memberId)).thenReturn(Optional.empty());

        // When & Then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> recipeReviewService.updateReview(memberId, reviewId, updateRequest)
        );
        assertEquals(ErrorCode.REVIEW_NOT_FOUND, exception.getErrorCode());
    }

    /**
     * 본인의 후기를 삭제할 때 정상적으로 repository.delete가 호출되는지 검증한다.
     */
    @Test
    void deleteReviewDeletesEntity() {
        // Given: 1번 회원의 100번 후기
        Long memberId = 1L;
        Long reviewId = 100L;
        Member member = createMember(memberId, "사용자1");
        RecipeReview review = createReview(reviewId, member, 10L, 4, "삭제할 후기");

        when(recipeReviewRepository.findByIdAndMemberId(reviewId, memberId)).thenReturn(Optional.of(review));

        // When
        recipeReviewService.deleteReview(memberId, reviewId);

        // Then
        verify(recipeReviewRepository).delete(review);
    }

    /**
     * 타인의 후기이거나 존재하지 않는 후기 삭제 시 404 REVIEW_NOT_FOUND 예외가 발생하고
     * delete가 호출되지 않는지 검증한다.
     */
    @Test
    void deleteReviewThrowsNotFoundWhenOwnedByAnotherMemberOrMissing() {
        // Given
        Long memberId = 1L;
        Long reviewId = 999L;

        when(recipeReviewRepository.findByIdAndMemberId(reviewId, memberId)).thenReturn(Optional.empty());

        // When & Then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> recipeReviewService.deleteReview(memberId, reviewId)
        );
        assertEquals(ErrorCode.REVIEW_NOT_FOUND, exception.getErrorCode());
        verify(recipeReviewRepository, never()).delete(any(RecipeReview.class));
    }
}
