package org.example.takecareofmyfridgebe.global.config;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.auth.service.AuthSessionService;
import org.example.takecareofmyfridgebe.domain.auth.service.JwtTokenService;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.example.takecareofmyfridgebe.global.filter.JwtCookieFilter;
import org.example.takecareofmyfridgebe.global.response.ApiResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfException;
import tools.jackson.databind.ObjectMapper;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenService jwtTokenService;
    private final AuthSessionService sessionService;
    private final ObjectMapper objectMapper;

    // OpenAPI 스펙(프론트 클라이언트 생성용)과 Swagger UI는 인증 없이 열어 둔다.
    private static final String[] API_DOCS_PATHS = {
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    };

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.spa().ignoringRequestMatchers(request ->
                        "POST".equals(request.getMethod())
                                && ((request.getContextPath() + "/api/members").equals(request.getRequestURI())
                                || (request.getContextPath() + "/api/auth/login").equals(request.getRequestURI()))))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST,
                                "/api/members"
                        ).permitAll()
                        .requestMatchers(
                                "/api/auth/login", "/api/auth/refresh", "/api/auth/logout", "/api/auth/csrf",
                                "/api/members/email-availability", "/api/members/nickname-availability",
                                "/api/products", "/api/products/**"
                        ).permitAll()
                        .requestMatchers(API_DOCS_PATHS).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/admin/members/*/promotion").authenticated()
                        .requestMatchers("/api/admin", "/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(401);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            objectMapper.writeValue(response.getWriter(), ApiResponse.error(ErrorCode.UNAUTHORIZED));
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            response.setStatus(403);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            ErrorCode code = exception instanceof CsrfException
                                    ? ErrorCode.FORBIDDEN : ErrorCode.ADMIN_ACCESS_DENIED;
                            objectMapper.writeValue(response.getWriter(), ApiResponse.error(code));
                        }))
                .addFilterBefore(new JwtCookieFilter(jwtTokenService, sessionService),
                        UsernamePasswordAuthenticationFilter.class)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable);
        return http.build();
    }
}
