package org.example.takecareofmyfridgebe.domain.cooking.service;

import org.example.takecareofmyfridgebe.domain.cooking.dto.CookingCompleteRequest;
import org.example.takecareofmyfridgebe.domain.cooking.dto.CookingLogResponse;
import org.example.takecareofmyfridgebe.domain.cooking.dto.CookingLogSearchRequest;
import org.example.takecareofmyfridgebe.domain.cooking.entity.CookingLog;
import org.example.takecareofmyfridgebe.domain.cooking.entity.CookingResult;
import org.example.takecareofmyfridgebe.domain.cooking.repository.CookingLogRepository;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.example.takecareofmyfridgebe.global.response.PageResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * CookingService 단위 테스트.
 * 요리 기록 저장 및 조회 조건(recipeId, from, to)과 DTO 변환(fromChat 여부),
 * from > to 유효성 검증을 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class CookingServiceTest {

    @Mock
    private CookingLogRepository cookingLogRepository;

    @InjectMocks
    private CookingService cookingService;

    /**
     * 정상적인 요리 기록 저장 요청 시 엔티티를 repository.save로 저장하는지 검증한다.
     */
    @Test
    void saveCookingLogSavesEntity() {
        // Given
        Long memberId = 7L;
        CookingCompleteRequest request = new CookingCompleteRequest(
                12L, CookingResult.SUCCESS, "맛있어요", 10L, List.of());
        CookingLog savedLog = request.toEntity(memberId);
        ReflectionTestUtils.setField(savedLog, "id", 100L);

        when(cookingLogRepository.save(any(CookingLog.class))).thenReturn(savedLog);

        // When
        CookingLog result = cookingService.saveCookingLog(memberId, request);

        // Then
        assertEquals(100L, result.getId());
        verify(cookingLogRepository).save(any(CookingLog.class));
    }

    /**
     * 조회 기간의 시작일(from)이 종료일(to)보다 늦으면
     * 400 INVALID_INPUT_VALUE 예외가 발생하고 repository 조회를 호출하지 않는지 검증한다.
     */
    @Test
    void getCookingLogsThrowsWhenFromIsAfterTo() {
        // Given
        CookingLogSearchRequest request = new CookingLogSearchRequest(
                null, LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 1), 1, 20);

        // When & Then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> cookingService.getCookingLogs(7L, request)
        );
        assertEquals(ErrorCode.INVALID_INPUT_VALUE, exception.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getErrorCode().getStatus());
        verifyNoInteractions(cookingLogRepository);
    }

    /**
     * 요리 기록 조회 시:
     * 1) 조건(recipeId, from, to)과 1부터 시작하는 page를 0부터 시작하는 PageRequest로 넘기는지
     * 2) chatSessionId 존재 여부에 따라 fromChat이 올바르게 매핑되는지 검증한다.
     */
    @Test
    void getCookingLogsMapsEntitiesToResponsesWithFromChatFlag() {
        // Given
        Long memberId = 7L;
        CookingLogSearchRequest request = new CookingLogSearchRequest(
                12L, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), 1, 20);

        CookingLog logWithChat = CookingLog.builder()
                .memberId(memberId)
                .recipeId(12L)
                .chatSessionId(5L)
                .result(CookingResult.SUCCESS)
                .memo("상담 후 요리")
                .build();
        ReflectionTestUtils.setField(logWithChat, "id", 101L);
        ReflectionTestUtils.setField(logWithChat, "createdAt", LocalDateTime.of(2026, 10, 15, 12, 0));

        CookingLog logWithoutChat = CookingLog.builder()
                .memberId(memberId)
                .recipeId(12L)
                .chatSessionId(null)
                .result(CookingResult.FAILED)
                .memo("직접 요리")
                .build();
        ReflectionTestUtils.setField(logWithoutChat, "id", 102L);
        ReflectionTestUtils.setField(logWithoutChat, "createdAt", LocalDateTime.of(2026, 10, 16, 18, 0));

        Page<CookingLog> page = new PageImpl<>(List.of(logWithChat, logWithoutChat));

        when(cookingLogRepository.findLogsByCondition(
                eq(memberId), eq(12L),
                eq(LocalDate.of(2026, 10, 1)), eq(LocalDate.of(2026, 10, 31)),
                eq(PageRequest.of(0, 20))
        )).thenReturn(page);

        // When
        PageResponse<CookingLogResponse> response = cookingService.getCookingLogs(memberId, request);

        // Then
        assertEquals(2, response.content().size());

        CookingLogResponse first = response.content().get(0);
        assertEquals(101L, first.id());
        assertTrue(first.fromChat());
        assertEquals(CookingResult.SUCCESS, first.result());
        assertEquals("상담 후 요리", first.memo());

        CookingLogResponse second = response.content().get(1);
        assertEquals(102L, second.id());
        assertFalse(second.fromChat());
        assertEquals(CookingResult.FAILED, second.result());
    }
}
