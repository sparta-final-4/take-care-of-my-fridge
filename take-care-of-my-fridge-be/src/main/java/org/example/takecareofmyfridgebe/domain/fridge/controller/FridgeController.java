package org.example.takecareofmyfridgebe.domain.fridge.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.auth.service.AuthSessionService.AuthPrincipal;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeItemCreateRequest;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeItemResponse;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeItemSearchRequest;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeItemUpdateRequest;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeRemoveReason;
import org.example.takecareofmyfridgebe.domain.fridge.facade.FridgeFacade;
import org.example.takecareofmyfridgebe.domain.fridge.service.FridgeService;
import org.example.takecareofmyfridgebe.global.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/fridge")
public class FridgeController {

    private final FridgeFacade fridgeFacade;
    private final FridgeService fridgeService;

    /**
     * 로그인한 회원 본인의 냉장고 재료 전체 목록을 조건(보관방식, 정렬, 임박 여부)으로 조회한다.
     *
     * @param principal 인증된 요청자의 회원 정보 — 토큰에서 추출한 회원 ID 소유의 재료만 조회 대상
     * @param request   조회 필터 및 정렬 조건 (storage, sort, imminentOnly)
     * @return 조건에 맞는 냉장고 재료 목록
     */
    @GetMapping("/items")
    public ResponseEntity<ApiResponse<List<FridgeItemResponse>>> getItems(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @ModelAttribute FridgeItemSearchRequest request
    ) {
        List<FridgeItemResponse> response = fridgeService.getItems(
                principal.memberId(),
                request.storage(),
                request.sort(),
                request.imminentOnly()
        );
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * 로그인한 회원의 냉장고에 여러 재료를 한 번에 등록한다.
     *
     * @param principal 인증된 요청자의 회원 정보 — 토큰에서 추출한 회원 ID로 재료 소유자 설정
     * @param request   등록할 재료 목록 요청
     * @return 등록된 냉장고 재료 목록
     */
    @PostMapping("/items")
    public ResponseEntity<ApiResponse<List<FridgeItemResponse>>> createItems(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody FridgeItemCreateRequest request
    ) {
        List<FridgeItemResponse> response = fridgeFacade.createItems(principal.memberId(), request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * 로그인한 회원 본인의 냉장고 재료 정보(이름, 수량, 보관방식, 소비기한)를 수정한다.
     *
     * @param principal 인증된 요청자의 회원 정보 — 토큰에서 추출한 회원 ID로 본인 재료 여부 검증
     * @param fridgeId  수정할 냉장고 재료 ID
     * @param request   수정할 재료 정보 (rawName, quantity, storage, expirationDate)
     * @return 수정된 냉장고 재료 정보
     */
    @PatchMapping("/items/{fridgeId}")
    public ResponseEntity<ApiResponse<FridgeItemResponse>> updateItem(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long fridgeId,
            @Valid @RequestBody FridgeItemUpdateRequest request
    ) {
        FridgeItemResponse response = fridgeFacade.updateItem(principal.memberId(), fridgeId, request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * 로그인한 회원 본인의 냉장고 재료를 빼서 삭제한다.
     * reason이 CONSUMED(다 먹었어요)·DISCARDED(버렸어요)이면 이력을 기록한 뒤 삭제하고,
     * WRONG_ENTRY(잘못 등록해서 삭제)이면 기록 없이 삭제한다.
     *
     * @param principal 인증된 요청자의 회원 정보 — 토큰에서 추출한 회원 ID로 본인 재료 여부 검증
     * @param fridgeId  삭제할 냉장고 재료 ID
     * @param reason    재료 빼기 사유 (CONSUMED, DISCARDED, WRONG_ENTRY 필수)
     * @return 빈 성공 응답
     */
    @DeleteMapping("/items/{fridgeId}")
    public ResponseEntity<ApiResponse<Void>> removeItem(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long fridgeId,
            @RequestParam FridgeRemoveReason reason
    ) {
        fridgeService.removeItem(principal.memberId(), fridgeId, reason);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
