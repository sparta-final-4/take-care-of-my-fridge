package org.example.takecareofmyfridgebe.domain.auth.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class AuthCookies {

    private final boolean secure;

    public AuthCookies(@Value("${app.auth.cookie-secure:true}") boolean secure) {
        this.secure = secure;
    }

    public ResponseCookie access(String token) {
        return ResponseCookie.from("access_token", token)
                .httpOnly(true).secure(secure).sameSite("Lax")
                .path("/api").maxAge(Duration.ofMinutes(15)).build();
    }

    public ResponseCookie refresh(String token) {
        return ResponseCookie.from("refresh_token", token)
                .httpOnly(true).secure(secure).sameSite("Lax")
                .path("/api/auth").maxAge(Duration.ofDays(7)).build();
    }
}
