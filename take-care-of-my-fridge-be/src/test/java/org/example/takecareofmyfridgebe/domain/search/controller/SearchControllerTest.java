package org.example.takecareofmyfridgebe.domain.search.controller;

import org.example.takecareofmyfridgebe.domain.auth.service.AuthSessionService;
import org.example.takecareofmyfridgebe.domain.auth.service.JwtTokenService;
import org.example.takecareofmyfridgebe.domain.product.dto.ProductListItemResponse;
import org.example.takecareofmyfridgebe.domain.product.dto.ProductListRequest;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductType;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeListItemResponse;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeListRequest;
import org.example.takecareofmyfridgebe.domain.search.facade.ProductSearchFacade;
import org.example.takecareofmyfridgebe.domain.search.facade.RecipeSearchFacade;
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
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SearchController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, SearchControllerTest.TestConfig.class})
class SearchControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RecipeSearchFacade recipeSearchFacade;

    @Autowired
    private ProductSearchFacade productSearchFacade;

    @BeforeEach
    void resetMock() {
        reset(recipeSearchFacade, productSearchFacade);
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
        JwtTokenService jwtTokenService() {
            return mock(JwtTokenService.class);
        }

        @Bean
        AuthSessionService authSessionService() {
            return mock(AuthSessionService.class);
        }
    }
}
