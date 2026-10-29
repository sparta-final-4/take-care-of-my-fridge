package org.example.takecareofmyfridgebe.domain.product.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.example.takecareofmyfridgebe.domain.auth.service.AuthSessionService;
import org.example.takecareofmyfridgebe.domain.auth.service.JwtTokenService;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.product.dto.AdminProductListRequest;
import org.example.takecareofmyfridgebe.domain.product.dto.ProductListItemResponse;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductType;
import org.example.takecareofmyfridgebe.domain.product.facade.ProductFacade;
import org.example.takecareofmyfridgebe.domain.product.service.ProductService;
import org.example.takecareofmyfridgebe.global.config.SecurityConfig;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.example.takecareofmyfridgebe.global.response.PageResponse;
import org.junit.jupiter.api.BeforeEach;
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

@WebMvcTest(AdminProductController.class)
@Import({SecurityConfig.class, AdminProductControllerTest.TestConfig.class})
class AdminProductControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ProductFacade facade;
    @Autowired
    private ProductService service;

    @BeforeEach
    void resetMock() {
        reset(facade, service);
    }

    @Test
    void adminReadsProductsWithFilters() throws Exception {
        when(facade.getAdminProducts(any(AdminProductListRequest.class))).thenReturn(new PageResponse<>(
                List.of(new ProductListItemResponse(5L, ProductType.INGREDIENT, "양파 1kg", null, 3900,
                        ProductStatus.STOPPED, null, true)),
                2, 10, 11, 2, true));

        mvc.perform(get("/api/admin/products")
                        .param("keyword", "양파").param("status", "STOPPED")
                        .param("page", "2").param("size", "10")
                        .with(authenticationFor(Member.Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].status").value("STOPPED"))
                .andExpect(jsonPath("$.data.pageNumber").value(2));
        verify(facade).getAdminProducts(
                new AdminProductListRequest("양파", null, ProductStatus.STOPPED, 2, 10));
    }

    @Test
    void rejectsPageSizeOverLimit() throws Exception {
        mvc.perform(get("/api/admin/products").param("size", "101")
                        .with(authenticationFor(Member.Role.ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("C001"));
        verifyNoInteractions(facade, service);
    }

    @Test
    void missingProductReturnsProduct001() throws Exception {
        when(facade.getAdminProduct(9L)).thenThrow(new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        mvc.perform(get("/api/admin/products/9")
                        .with(authenticationFor(Member.Role.ADMIN)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PRODUCT_001"));
    }

    @Test
    void userAndAnonymousCannotReadAdminProducts() throws Exception {
        mvc.perform(get("/api/admin/products").with(authenticationFor(Member.Role.USER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("A004"));
        mvc.perform(get("/api/admin/products/5").with(authenticationFor(Member.Role.USER)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/products"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(facade, service);
    }

    private RequestPostProcessor authenticationFor(Member.Role role) {
        return authentication(new UsernamePasswordAuthenticationToken(
                new AuthSessionService.AuthPrincipal(7L, 8L, role), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        ProductFacade productFacade() {
            return mock(ProductFacade.class);
        }

        @Bean
        ProductService productService() {
            return mock(ProductService.class);
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
