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
import tools.jackson.databind.ObjectMapper;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenService jwtTokenService;
    private final AuthSessionService sessionService;
    private final ObjectMapper objectMapper;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.spa().ignoringRequestMatchers(request ->
                        "POST".equals(request.getMethod())
                                && ((request.getContextPath() + "/api/members").equals(request.getRequestURI())
                                || (request.getContextPath() + "/api/auth/login").equals(request.getRequestURI()))))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/members").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/members/email-availability").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/members/nickname-availability").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/refresh").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
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
                            objectMapper.writeValue(response.getWriter(), ApiResponse.error(ErrorCode.FORBIDDEN));
                        }))
                .addFilterBefore(new JwtCookieFilter(jwtTokenService, sessionService),
                        UsernamePasswordAuthenticationFilter.class)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable);
        return http.build();
    }
}
