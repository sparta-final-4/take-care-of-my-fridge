package org.example.takecareofmyfridgebe.domain.auth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.auth.dto.LoginMember;
import org.example.takecareofmyfridgebe.domain.auth.dto.LoginRequest;
import org.example.takecareofmyfridgebe.domain.auth.service.LoginService;
import org.example.takecareofmyfridgebe.domain.auth.service.RefreshService;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.example.takecareofmyfridgebe.global.response.ApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final LoginService loginService;
    private final RefreshService refreshService;
    private final AuthCookies cookies;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginMember>> login(@Valid @RequestBody LoginRequest request) {
        LoginService.LoginResult result = loginService.login(request.email(), request.password());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.access(result.accessToken()).toString())
                .header(HttpHeaders.SET_COOKIE, cookies.refresh(result.refreshToken()).toString())
                .body(ApiResponse.ok(result.member()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Void>> refresh(
            @CookieValue(value = "refresh_token", required = false) String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        RefreshService.RefreshedTokens tokens = refreshService.refresh(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.access(tokens.accessToken()).toString())
                .header(HttpHeaders.SET_COOKIE,
                        cookies.refresh(tokens.refreshToken(), tokens.refreshMaxAge()).toString())
                .body(ApiResponse.ok());
    }
}
