package org.example.takecareofmyfridgebe.domain.cooking.repository;

import org.example.takecareofmyfridgebe.domain.cooking.entity.CookingLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

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
}
