package org.example.takecareofmyfridgebe.domain.search.controller;

import org.example.takecareofmyfridgebe.domain.auth.service.AuthSessionService;
import org.example.takecareofmyfridgebe.domain.auth.service.JwtTokenService;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.product.dto.ProductListItemResponse;
import org.example.takecareofmyfridgebe.domain.product.dto.ProductListRequest;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductType;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeListItemResponse;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeListRequest;
import org.example.takecareofmyfridgebe.domain.search.dto.PopularKeywordsResponse;
import org.example.takecareofmyfridgebe.domain.search.dto.SearchClickRequest;
import org.example.takecareofmyfridgebe.domain.search.dto.SearchFridgeMatchRequest;
import org.example.takecareofmyfridgebe.domain.search.dto.SearchFridgeMatchResponse;
import org.example.takecareofmyfridgebe.domain.search.dto.SearchRecipeClickRequest;
import org.example.takecareofmyfridgebe.domain.search.facade.ProductSearchFacade;
import org.example.takecareofmyfridgebe.domain.search.facade.RecipeSearchFacade;
import org.example.takecareofmyfridgebe.domain.search.facade.SearchFridgeMatchFacade;
import org.example.takecareofmyfridgebe.domain.search.service.SearchClickHistoryService;
import org.example.takecareofmyfridgebe.domain.search.service.SearchPopularKeywordService;
import org.example.takecareofmyfridgebe.global.config.security.SecurityConfig;
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
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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

    private static final String RECIPE_CLICK_BODY = """
            {"recipeId": 3, "keyword": "김치"}
            """;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RecipeSearchFacade recipeSearchFacade;

    @Autowired
    private ProductSearchFacade productSearchFacade;

    @Autowired
    private SearchClickHistoryService searchClickHistoryService;

    @Autowired
    private SearchFridgeMatchFacade searchFridgeMatchFacade;

    @Autowired
    private SearchPopularKeywordService searchPopularKeywordService;

    @BeforeEach
    void resetMock() {
        reset(recipeSearchFacade, productSearchFacade, searchClickHistoryService, searchFridgeMatchFacade,
                searchPopularKeywordService);
    }

    @Test
    @DisplayName("비로그인 상태에서 레시피를 검색하면 200 OK와 검색 결과 페이지를 반환한다")
    void searchRecipes_returnsOk_whenUnauthenticated() throws Exception {
        // Given
        PageResponse<RecipeListItemResponse> page = new PageResponse<>(List.of(), 1, 20, 0, 0, true);
        when(recipeSearchFacade.getRecipes(any(RecipeListRequest.class), isNull())).thenReturn(page);

        // When & Then
        mvc.perform(get("/api/search/recipes").param("keyword", "계란").param("cuisineType", "한식"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.pageNumber").value(1));

        ArgumentCaptor<RecipeListRequest> captor = ArgumentCaptor.forClass(RecipeListRequest.class);
        verify(recipeSearchFacade).getRecipes(captor.capture(), isNull());
        assertThat(captor.getValue().keyword()).isEqualTo("계란");
        assertThat(captor.getValue().cuisineType()).isEqualTo("한식");
    }

    @Test
    @DisplayName("페이지 크기가 허용 범위를 벗어나면 400 Bad Request를 반환한다")
    void searchRecipes_returnsBadRequest_whenSizeInvalid() throws Exception {
        // When & Then
        mvc.perform(get("/api/search/recipes").param("keyword", "계란").param("size", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("비로그인 상태에서 상품을 검색하면 200 OK와 검색 결과 페이지를 반환한다")
    void searchProducts_returnsOk_whenUnauthenticated() throws Exception {
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
        assertThat(captor.getValue().keyword()).isEqualTo("대파");
        assertThat(captor.getValue().type()).isEqualTo(ProductType.INGREDIENT);
    }

    @Test
    @DisplayName("상품 검색의 가격 범위가 뒤집혀 있으면 400 Bad Request를 반환한다")
    void searchProducts_returnsBadRequest_whenPriceRangeInvalid() throws Exception {
        // When & Then
        mvc.perform(get("/api/search/products").param("keyword", "대파")
                        .param("minPrice", "5000").param("maxPrice", "1000"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("비로그인 상태로 클릭을 보내면 회원 번호 없이 기록하고 데이터 없이 200이다")
    void recordClick_returnsOk_whenUnauthenticated() throws Exception {
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
    void recordClick_passesMemberId_whenAuthenticated() throws Exception {
        mvc.perform(post("/api/search/clicks").with(csrf()).with(loginAsMember7())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CLICK_BODY))
                .andExpect(status().isOk());

        verify(searchClickHistoryService).record(new SearchClickRequest(10L, "계란"), 7L);
    }

    @Test
    @DisplayName("상품 번호가 없거나 검색어가 비어 있으면 400이고 기록하지 않는다")
    void recordClick_returnsBadRequest_whenRequestInvalid() throws Exception {
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
    void recordClick_returnsBadRequest_whenProductIdNonPositive() throws Exception {
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
    void recordClick_returnsForbidden_whenCsrfMissing() throws Exception {
        mvc.perform(post("/api/search/clicks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CLICK_BODY))
                .andExpect(status().isForbidden());

        verifyNoInteractions(searchClickHistoryService);
    }

    @Test
    @DisplayName("비로그인 상태로 레시피 클릭을 보내면 회원 번호 없이 기록하고 데이터 없이 200이다")
    void recordRecipeClick_returnsOk_whenUnauthenticated() throws Exception {
        mvc.perform(post("/api/search/recipe-clicks").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(RECIPE_CLICK_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(searchClickHistoryService).recordRecipe(new SearchRecipeClickRequest(3L, "김치"), null);
    }

    @Test
    @DisplayName("로그인 상태로 레시피 클릭을 보내면 로그인한 회원 번호와 함께 기록한다")
    void recordRecipeClick_passesMemberId_whenAuthenticated() throws Exception {
        mvc.perform(post("/api/search/recipe-clicks").with(csrf()).with(loginAsMember7())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(RECIPE_CLICK_BODY))
                .andExpect(status().isOk());

        verify(searchClickHistoryService).recordRecipe(new SearchRecipeClickRequest(3L, "김치"), 7L);
    }

    @Test
    @DisplayName("레시피 번호가 0이거나 검색어가 비어 있으면 400이고 기록하지 않는다")
    void recordRecipeClick_returnsBadRequest_whenRequestInvalid() throws Exception {
        mvc.perform(post("/api/search/recipe-clicks").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipeId": 0, "keyword": " "}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(searchClickHistoryService);
    }

    @Test
    @DisplayName("비로그인 상태에서 자동완성을 요청하면 앞뒤 공백을 뺀 검색어로 후보 이름 목록을 반환한다")
    void suggest_returnsOk_whenUnauthenticated() throws Exception {
        // Given
        when(recipeSearchFacade.suggestNames("김치")).thenReturn(List.of("김치찌개", "김치볶음밥"));

        // When & Then
        mvc.perform(get("/api/search/suggestions").param("keyword", " 김치 "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0]").value("김치찌개"))
                .andExpect(jsonPath("$.data[1]").value("김치볶음밥"));
    }

    @Test
    @DisplayName("자동완성 검색어가 비어 있거나 50자를 넘으면 400이고 조회하지 않는다")
    void suggest_returnsBadRequest_whenKeywordInvalid() throws Exception {
        mvc.perform(get("/api/search/suggestions").param("keyword", " "))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/search/suggestions").param("keyword", "가".repeat(51)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(recipeSearchFacade);
    }

    @Test
    @DisplayName("로그인 회원이 레시피를 검색하면 회원 ID를 함께 넘긴다")
    void searchRecipes_passesMemberId_whenAuthenticated() throws Exception {
        // Given
        PageResponse<RecipeListItemResponse> page = new PageResponse<>(List.of(), 1, 20, 0, 0, true);
        when(recipeSearchFacade.getRecipes(any(RecipeListRequest.class), eq(7L))).thenReturn(page);

        // When & Then
        mvc.perform(get("/api/search/recipes").with(loginAsMember7()).param("keyword", "계란"))
                .andExpect(status().isOk());
        verify(recipeSearchFacade).getRecipes(any(RecipeListRequest.class), eq(7L));
    }

    @Test
    @DisplayName("비로그인 상태에서 인기 검색어를 요청하면 200 OK와 검색어 목록을 반환한다")
    void getPopularKeywords_returnsOk_whenUnauthenticated() throws Exception {
        // Given
        when(searchPopularKeywordService.getTopKeywords())
                .thenReturn(new PopularKeywordsResponse(List.of("김치찌개", "두부")));

        // When & Then
        mvc.perform(get("/api/search/popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.keywords[0]").value("김치찌개"))
                .andExpect(jsonPath("$.data.keywords[1]").value("두부"));
    }

    @Test
    @DisplayName("로그인 회원이 냉장고 비교를 요청하면 회원 ID와 레시피 ID·검색어를 넘겨 레시피별 보유 재료 수를 반환한다")
    void matchFridge_returnsOk_whenAuthenticated() throws Exception {
        // Given
        when(searchFridgeMatchFacade.match(eq(7L), any(SearchFridgeMatchRequest.class))).thenReturn(
                new SearchFridgeMatchResponse(List.of(new SearchFridgeMatchResponse.RecipeMatch(3L, 2, 5)), List.of("국산 흙대파")));

        // When & Then
        mvc.perform(get("/api/search/fridge-matches").with(loginAsMember7())
                        .param("recipeIds", "3", "4").param("keyword", " 대파 "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recipes[0].recipeId").value(3))
                .andExpect(jsonPath("$.data.recipes[0].ownedCount").value(2))
                .andExpect(jsonPath("$.data.recipes[0].ingredientCount").value(5))
                .andExpect(jsonPath("$.data.keywordItems[0]").value("국산 흙대파"));

        ArgumentCaptor<SearchFridgeMatchRequest> captor = ArgumentCaptor.forClass(SearchFridgeMatchRequest.class);
        verify(searchFridgeMatchFacade).match(eq(7L), captor.capture());
        assertThat(captor.getValue().recipeIds()).containsExactly(3L, 4L);
        assertThat(captor.getValue().keyword()).isEqualTo("대파");
    }

    @Test
    @DisplayName("비로그인 상태에서 냉장고 비교를 요청하면 401이고 조회하지 않는다")
    void matchFridge_returnsUnauthorized_whenUnauthenticated() throws Exception {
        mvc.perform(get("/api/search/fridge-matches").param("recipeIds", "3"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(searchFridgeMatchFacade);
    }

    @Test
    @DisplayName("레시피 ID가 없거나 20개를 넘으면 400이고 조회하지 않는다")
    void matchFridge_returnsBadRequest_whenRecipeIdsInvalid() throws Exception {
        mvc.perform(get("/api/search/fridge-matches").with(loginAsMember7()))
                .andExpect(status().isBadRequest());
        String[] tooMany = IntStream.rangeClosed(1, 21).mapToObj(String::valueOf).toArray(String[]::new);
        mvc.perform(get("/api/search/fridge-matches").with(loginAsMember7()).param("recipeIds", tooMany))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(searchFridgeMatchFacade);
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
        SearchPopularKeywordService searchPopularKeywordService() {
            return mock(SearchPopularKeywordService.class);
        }

        @Bean
        SearchFridgeMatchFacade searchFridgeMatchFacade() {
            return mock(SearchFridgeMatchFacade.class);
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
