package org.example.takecareofmyfridgebe.domain.cooking.service;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.cooking.dto.CookingCompleteRequest;
import org.example.takecareofmyfridgebe.domain.cooking.entity.CookingLog;
import org.example.takecareofmyfridgebe.domain.cooking.repository.CookingLogRepository;
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
}
