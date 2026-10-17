package org.example.takecareofmyfridgebe.domain.member.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.member.dto.EmailAvailabilityRequest;
import org.example.takecareofmyfridgebe.domain.member.dto.EmailAvailabilityResponse;
import org.example.takecareofmyfridgebe.domain.member.dto.SignUpRequest;
import org.example.takecareofmyfridgebe.domain.member.dto.SignUpResponse;
import org.example.takecareofmyfridgebe.domain.member.service.MemberService;
import org.example.takecareofmyfridgebe.global.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @GetMapping("/email-availability")
    public ResponseEntity<ApiResponse<EmailAvailabilityResponse>> checkEmailAvailability(
            @Valid @ModelAttribute EmailAvailabilityRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(memberService.checkEmailAvailability(request.email())));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SignUpResponse>> signUp(@Valid @RequestBody SignUpRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(memberService.signUp(request)));
    }
}
