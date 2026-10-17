package org.example.takecareofmyfridgebe.domain.auth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.auth.dto.LoginRequest;
import org.example.takecareofmyfridgebe.domain.auth.service.LoginService;
import org.example.takecareofmyfridgebe.domain.member.dto.MyInfoResponse;
import org.example.takecareofmyfridgebe.global.response.ApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final LoginService loginService;
    private final AuthCookies cookies;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<MyInfoResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginService.LoginResult result = loginService.login(request.email(), request.password());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.access(result.accessToken()).toString())
                .header(HttpHeaders.SET_COOKIE, cookies.refresh(result.refreshToken()).toString())
                .body(ApiResponse.ok(result.member()));
    }
}
