package org.example.takecareofmyfridgebe.domain.review.service;

import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.member.repository.MemberRepository;
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

    @InjectMocks
    private RecipeReviewService recipeReviewService;

    private Member createMember(Long id, String nickname) {
        Member member = Member.create("user" + id + "@example.com", "encoded_password", nickname, Collections.emptySet());
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
        ReflectionTestUtils.setField(review, "createdAt", LocalDateTime.now());
        ReflectionTestUtils.setField(review, "updatedAt", LocalDateTime.now());
        return review;
    }

    /**
     * mine=true 조건으로 조회할 때 로그인 상태가 아니면(currentMemberId == null)
     * 401 UNAUTHORIZED 에러를 반환하는지 검증한다.
     */
    @Test
    void getReviewsThrowsUnauthorizedWhenMineIsTrueAndNotLoggedIn() {
        // Given: mine=true 조건 및 비로그인 상태
        ReviewSearchRequest request = new ReviewSearchRequest(10L, true, ReviewSortType.LATEST, 1, 10);

        // When & Then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> recipeReviewService.getReviews(request, null)
        );
        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
    }

    /**
     * 후기 목록 조회 시 작성자 닉네임과 로그인 회원 기준 mine 여부(본인 후기 true, 타인 후기 false)가
     * ReviewResponse로 올바르게 변환되는지 검증한다.
     */
    @Test
    void getReviewsMapsEntitiesToResponsesWithNicknameAndMineFlag() {
        // Given: 1번 회원(본인)과 2번 회원(타인)이 작성한 후기 엔티티 2건
        Member member1 = createMember(1L, "작성자1");
        Member member2 = createMember(2L, "작성자2");
        RecipeReview review1 = createReview(101L, member1, 10L, 5, "맛있어요!");
        RecipeReview review2 = createReview(102L, member2, 10L, 4, "좋아요");

        Page<RecipeReview> reviewPage = new PageImpl<>(List.of(review1, review2));
        ReviewSearchRequest request = new ReviewSearchRequest(10L, false, ReviewSortType.LATEST, 1, 10);

        when(recipeReviewRepository.findReviewsByCondition(request, 1L)).thenReturn(reviewPage);

        // When
        PageResponse<ReviewResponse> response = recipeReviewService.getReviews(request, 1L);

        // Then
        assertEquals(2, response.content().size());

        ReviewResponse first = response.content().get(0);
        assertEquals(101L, first.id());
        assertEquals("작성자1", first.nickname());
        assertTrue(first.mine());

        ReviewResponse second = response.content().get(1);
        assertEquals(102L, second.id());
        assertEquals("작성자2", second.nickname());
        assertFalse(second.mine());
    }

    /**
     * 정상적인 후기 작성 요청 시 memberRepository.findById로 회원을 조회하고
     * 저장된 엔티티를 ReviewResponse로 변환하여 반환하는지 검증한다.
     */
    @Test
    void createReviewSavesReviewAndReturnsResponse() {
        // Given: 1번 회원의 10번 레시피 후기 작성 요청
        Long memberId = 1L;
        Member member = createMember(memberId, "사용자1");
        ReviewCreateRequest request = new ReviewCreateRequest(10L, 5, "정말 맛있어요!");

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
