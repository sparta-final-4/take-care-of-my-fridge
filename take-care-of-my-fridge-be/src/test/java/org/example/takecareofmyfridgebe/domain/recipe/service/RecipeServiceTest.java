package org.example.takecareofmyfridgebe.domain.recipe.service;

import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeListItemResponse;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeSort;
import org.example.takecareofmyfridgebe.domain.recipe.entity.Recipe;
import org.example.takecareofmyfridgebe.domain.recipe.repository.RecipeRepository;
import org.example.takecareofmyfridgebe.dummy.RecipeFixture;
import org.example.takecareofmyfridgebe.global.response.PageResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecipeServiceTest {

    @Mock
    private RecipeRepository recipeRepository;
    @InjectMocks
    private RecipeService service;

    @Test
    @DisplayName("1부터 시작하는 요청 페이지를 0부터 시작하는 PageRequest로 바꾸고 정렬을 적용한다")
    void getRecipes_convertsPageAndSort() {
        // given
        when(recipeRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        // when
        service.getRecipes(RecipeSort.DIFFICULTY_ASC, 3, 10);

        // then
        verify(recipeRepository).findAll(PageRequest.of(2, 10, RecipeSort.DIFFICULTY_ASC.toSort()));
    }

    @Test
    @DisplayName("조회한 레시피를 목록 응답 DTO와 1부터 시작하는 페이지 정보로 변환한다")
    void getRecipes_mapsToResponse() {
        // given
        Recipe stew = RecipeFixture.createRecipe(7L, "된장찌개");
        when(recipeRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(stew), PageRequest.of(0, 20), 1));

        // when
        PageResponse<RecipeListItemResponse> result = service.getRecipes(RecipeSort.LATEST, 1, 20);

        // then
        assertThat(result.pageNumber()).isEqualTo(1);
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.content()).containsExactly(new RecipeListItemResponse(
                7L, "된장찌개", "된장찌개 만드는 법", null, "한식", "국", 30, 2, 3, new BigDecimal("350.00")));
    }
}
