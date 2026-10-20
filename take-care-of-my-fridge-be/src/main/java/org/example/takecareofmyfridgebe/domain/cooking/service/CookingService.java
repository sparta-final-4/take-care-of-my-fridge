package org.example.takecareofmyfridgebe.domain.cooking.service;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.cooking.dto.CookingCompleteRequest;
import org.example.takecareofmyfridgebe.domain.cooking.dto.CookingLogResponse;
import org.example.takecareofmyfridgebe.domain.cooking.dto.CookingLogSearchRequest;
import org.example.takecareofmyfridgebe.domain.cooking.entity.CookingLog;
import org.example.takecareofmyfridgebe.domain.cooking.repository.CookingLogRepository;
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

    /**
     * 요리 완료 기록을 생성하여 저장한다.
     *
     * @param memberId 회원 ID
     * @param request  요리 완료 요청 정보
     * @return 저장된 요리 기록 엔티티
     */
    @Transactional
    public CookingLog saveCookingLog(Long memberId, CookingCompleteRequest request) {
        // TODO: 레시피 도메인에 RecipeRepository/Service가 구현되면 레시피 존재 여부 검증 추가

        CookingLog cookingLog = request.toEntity(memberId);
        return cookingLogRepository.save(cookingLog);
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
}
