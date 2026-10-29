package org.example.takecareofmyfridgebe.domain.search.controller;

import org.example.takecareofmyfridgebe.domain.auth.service.AuthSessionService;
import org.example.takecareofmyfridgebe.domain.auth.service.JwtTokenService;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.product.dto.ProductListItemResponse;
import org.example.takecareofmyfridgebe.domain.product.dto.ProductListRequest;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductType;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeListItemResponse;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeListRequest;
import org.example.takecareofmyfridgebe.domain.search.dto.SearchClickRequest;
import org.example.takecareofmyfridgebe.domain.search.facade.ProductSearchFacade;
import org.example.takecareofmyfridgebe.domain.search.facade.RecipeSearchFacade;
import org.example.takecareofmyfridgebe.domain.search.service.SearchClickHistoryService;
import org.example.takecareofmyfridgebe.global.config.SecurityConfig;
import org.example.takecareofmyfridgebe.global.error.GlobalExceptionHandler;
import org.example.takecareofmyfridgebe.global.response.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SearchController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, SearchControllerTest.TestConfig.class})
class SearchControllerTest {

    private static final String CLICK_BODY = """
            {"productId": 10, "keyword": "계란"}
            """;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RecipeSearchFacade recipeSearchFacade;

    @Autowired
    private ProductSearchFacade productSearchFacade;

    @Autowired
    private SearchClickHistoryService searchClickHistoryService;

    @BeforeEach
    void resetMock() {
        reset(recipeSearchFacade, productSearchFacade, searchClickHistoryService);
    }

    @Test
    @DisplayName("비로그인 상태에서 레시피를 검색하면 200 OK와 검색 결과 페이지를 반환한다")
    void searchRecipesUnauthenticatedReturnsOk() throws Exception {
        // Given
        PageResponse<RecipeListItemResponse> page = new PageResponse<>(List.of(), 1, 20, 0, 0, true);
        when(recipeSearchFacade.getRecipes(any(RecipeListRequest.class))).thenReturn(page);

        // When & Then
        mvc.perform(get("/api/search/recipes").param("keyword", "계란").param("cuisineType", "한식"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.pageNumber").value(1));

        ArgumentCaptor<RecipeListRequest> captor = ArgumentCaptor.forClass(RecipeListRequest.class);
        verify(recipeSearchFacade).getRecipes(captor.capture());
        assertEquals("계란", captor.getValue().keyword());
        assertEquals("한식", captor.getValue().cuisineType());
    }

    @Test
    @DisplayName("페이지 크기가 허용 범위를 벗어나면 400 Bad Request를 반환한다")
    void searchRecipesInvalidSizeReturnsBadRequest() throws Exception {
        // When & Then
        mvc.perform(get("/api/search/recipes").param("keyword", "계란").param("size", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("비로그인 상태에서 상품을 검색하면 200 OK와 검색 결과 페이지를 반환한다")
    void searchProductsUnauthenticatedReturnsOk() throws Exception {
        // Given
        PageResponse<ProductListItemResponse> page = new PageResponse<>(List.of(), 1, 20, 0, 0, true);
        when(productSearchFacade.getProducts(any(ProductListRequest.class))).thenReturn(page);

        // When & Then
        mvc.perform(get("/api/search/products").param("keyword", "대파").param("type", "INGREDIENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.pageNumber").value(1));

        ArgumentCaptor<ProductListRequest> captor = ArgumentCaptor.forClass(ProductListRequest.class);
        verify(productSearchFacade).getProducts(captor.capture());
        assertEquals("대파", captor.getValue().keyword());
        assertEquals(ProductType.INGREDIENT, captor.getValue().type());
    }

    @Test
    @DisplayName("상품 검색의 가격 범위가 뒤집혀 있으면 400 Bad Request를 반환한다")
    void searchProductsInvalidPriceRangeReturnsBadRequest() throws Exception {
        // When & Then
        mvc.perform(get("/api/search/products").param("keyword", "대파")
                        .param("minPrice", "5000").param("maxPrice", "1000"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("비로그인 상태로 클릭을 보내면 회원 번호 없이 기록하고 데이터 없이 200이다")
    void recordClickUnauthenticatedReturnsOk() throws Exception {
        mvc.perform(post("/api/search/clicks").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CLICK_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(searchClickHistoryService).record(new SearchClickRequest(10L, "계란"), null);
    }

    @Test
    @DisplayName("로그인 상태로 클릭을 보내면 로그인한 회원 번호와 함께 기록한다")
    void recordClickAuthenticatedPassesMemberId() throws Exception {
        mvc.perform(post("/api/search/clicks").with(csrf()).with(loginAsMember7())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CLICK_BODY))
                .andExpect(status().isOk());

        verify(searchClickHistoryService).record(new SearchClickRequest(10L, "계란"), 7L);
    }

    @Test
    @DisplayName("상품 번호가 없거나 검색어가 비어 있으면 400이고 기록하지 않는다")
    void recordClickInvalidReturnsBadRequest() throws Exception {
        mvc.perform(post("/api/search/clicks").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId": null, "keyword": " "}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(searchClickHistoryService);
    }

    @Test
    @DisplayName("상품 번호가 0이면 400이고 기록하지 않는다")
    void recordClickNonPositiveProductIdReturnsBadRequest() throws Exception {
        mvc.perform(post("/api/search/clicks").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId": 0, "keyword": "계란"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(searchClickHistoryService);
    }

    @Test
    @DisplayName("CSRF 토큰 없이 클릭을 보내면 403이고 기록하지 않는다")
    void recordClickWithoutCsrfReturnsForbidden() throws Exception {
        mvc.perform(post("/api/search/clicks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CLICK_BODY))
                .andExpect(status().isForbidden());

        verifyNoInteractions(searchClickHistoryService);
    }

    private RequestPostProcessor loginAsMember7() {
        return authentication(new UsernamePasswordAuthenticationToken(
                new AuthSessionService.AuthPrincipal(7L, 8L, Member.Role.USER), null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        RecipeSearchFacade recipeSearchFacade() {
            return mock(RecipeSearchFacade.class);
        }

        @Bean
        ProductSearchFacade productSearchFacade() {
            return mock(ProductSearchFacade.class);
        }

        @Bean
        SearchClickHistoryService searchClickHistoryService() {
            return mock(SearchClickHistoryService.class);
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
