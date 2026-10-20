package org.example.takecareofmyfridgebe.domain.cart.controller;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.example.takecareofmyfridgebe.domain.auth.service.AuthSessionService;
import org.example.takecareofmyfridgebe.domain.auth.service.JwtTokenService;
import org.example.takecareofmyfridgebe.domain.cart.service.CartService;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.global.config.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(CartController.class)
@Import({SecurityConfig.class, CartControllerTest.TestConfig.class})
class CartControllerTest {

    private final MockMvc mvc;
    private final CartService cartService;

    @Autowired
    CartControllerTest(MockMvc mvc, CartService cartService) {
        this.mvc = mvc;
        this.cartService = cartService;
    }

    @BeforeEach
    void resetMock() {
        reset(cartService);
    }

    @Test
    @DisplayName("줄 번호 목록으로 삭제를 요청하면 200이고 번호가 서비스에 그대로 전달된다")
    void removeItems_passesIdsToService() throws Exception {
        mvc.perform(delete("/api/cart-items")
                        .param("cartItemIds", "11,12")
                        .with(csrf())
                        .with(loginAsMember7()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(cartService).removeItems(eq(7L), argThat(request -> request.cartItemIds().equals(List.of(11L, 12L))));
    }

    @Test
    @DisplayName("줄 번호가 없으면 400이다")
    void removeItems_rejectsMissingIds() throws Exception {
        mvc.perform(delete("/api/cart-items")
                        .with(csrf())
                        .with(loginAsMember7()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("C001"));

        verifyNoInteractions(cartService);
    }

    @Test
    @DisplayName("줄 번호가 비어 있으면 400이다")
    void removeItems_rejectsEmptyIds() throws Exception {
        mvc.perform(delete("/api/cart-items")
                        .param("cartItemIds", "")
                        .with(csrf())
                        .with(loginAsMember7()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("C001"));

        verifyNoInteractions(cartService);
    }

    @Test
    @DisplayName("줄 번호가 숫자가 아니면 400이다")
    void removeItems_rejectsNonNumericIds() throws Exception {
        mvc.perform(delete("/api/cart-items")
                        .param("cartItemIds", "a,b")
                        .with(csrf())
                        .with(loginAsMember7()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("C001"));

        verifyNoInteractions(cartService);
    }

    private RequestPostProcessor loginAsMember7() {
        return authentication(new UsernamePasswordAuthenticationToken(
                new AuthSessionService.AuthPrincipal(7L, 8L, Member.Role.USER), null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        CartService cartService() {
            return mock(CartService.class);
        }

        @Bean
        JwtTokenService jwtTokenService() {
            return mock(JwtTokenService.class);
        }

        @Bean
        AuthSessionService authSessionService() {
            return mock(AuthSessionService.class);
        }
    }
}
