package org.example.takecareofmyfridgebe.domain.admin.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.admin.dto.MemberListRequest;
import org.example.takecareofmyfridgebe.domain.admin.dto.MemberSummaryResponse;
import org.example.takecareofmyfridgebe.domain.admin.facade.MemberPromotionFacade;
import org.example.takecareofmyfridgebe.domain.admin.service.MemberListService;
import org.example.takecareofmyfridgebe.domain.auth.service.AuthSessionService;
import org.example.takecareofmyfridgebe.global.response.ApiResponse;
import org.example.takecareofmyfridgebe.global.response.PageResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/members")
@RequiredArgsConstructor
public class AdminMemberController {

    private final MemberListService memberListService;
    private final MemberPromotionFacade memberPromotionFacade;

    @PostMapping("/{memberId}/promotion")
    public ResponseEntity<ApiResponse<Void>> promote(@PathVariable Long memberId,
            @AuthenticationPrincipal AuthSessionService.AuthPrincipal principal) {
        memberPromotionFacade.promote(principal.memberId(), principal.role(), memberId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<MemberSummaryResponse>>> getMembers(
            @Valid @ModelAttribute MemberListRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(memberListService.getMembers(
                request.keyword(), request.role(), request.page(), request.size())));
    }
}
