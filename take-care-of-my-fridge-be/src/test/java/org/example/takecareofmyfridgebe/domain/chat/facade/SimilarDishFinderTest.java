package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.ai.SimilarDishGenerator;
import org.example.takecareofmyfridgebe.domain.chat.dto.DishListKind;
import org.example.takecareofmyfridgebe.domain.chat.service.SimilarDishStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SimilarDishFinderTest {

    private static final Long MEMBER_ID = 1L;

    private SimilarDishStore store;
    private SimilarDishGenerator generator;
    private SimilarDishFinder finder;

    @BeforeEach
    void setUp() {
        store = mock(SimilarDishStore.class);
        generator = mock(SimilarDishGenerator.class);
        when(store.find(any(), any(), any())).thenReturn(Optional.empty());
        finder = new SimilarDishFinder(store, generator);
    }

    @Test
    @DisplayName("캐시가 있으면 AI를 부르지 않고 캐시 값을 쓴다")
    void usesCache_withoutCallingAi() {
        when(store.find(MEMBER_ID, DishListKind.SIMILAR, "김피탕")).thenReturn(Optional.of(List.of("탕수육", "깐풍기")));

        assertThat(finder.find(MEMBER_ID, "김피탕")).containsExactly("탕수육", "깐풍기");
        verify(generator, never()).generate(any(), any(), any());
    }

    @Test
    @DisplayName("캐시가 없으면 AI를 한 번 부르고, 요리 이름 자신·빈 이름·띄어쓰기만 다른 중복을 빼고 5개까지 저장한다")
    void callsAiAndSavesCleaned_whenNotCached() {
        when(generator.generate(MEMBER_ID, DishListKind.SIMILAR, "나가사키 짬뽕")).thenReturn(
                List.of("나가사키짬뽕", " 짬뽕 ", "", "짬뽕", "짬뽕밥", "해물짬뽕", "우동", "기스면", "울면"));

        List<String> dishes = finder.find(MEMBER_ID, "나가사키 짬뽕");

        assertThat(dishes).containsExactly("짬뽕", "짬뽕밥", "해물짬뽕", "우동", "기스면");
        verify(store).save(MEMBER_ID, DishListKind.SIMILAR, "나가사키 짬뽕", dishes);
    }

    @Test
    @DisplayName("AI 호출이 실패하면 예외를 내지 않고 빈 목록으로 진행하며 캐시에 저장하지 않는다")
    void returnsEmpty_whenAiFails() {
        when(generator.generate(any(), any(), any())).thenThrow(new IllegalStateException("timeout"));

        assertThat(finder.find(MEMBER_ID, "김피탕")).isEmpty();
        verify(store, never()).save(any(), any(), any(), any());
    }

    @Test
    @DisplayName("요리 이름이 없으면 캐시도 AI도 보지 않는다")
    void skips_whenNoDish() {
        assertThat(finder.find(MEMBER_ID, " ")).isEmpty();
        verify(store, never()).find(any(), any(), any());
        verify(generator, never()).generate(any(), any(), any());
    }

    @Test
    @DisplayName("넓은 요청은 넓은 요청 종류의 캐시를 먼저 보고, 있으면 AI를 부르지 않는다")
    void usesBroadCache_withoutCallingAi() {
        when(store.find(MEMBER_ID, DishListKind.BROAD, "술안주")).thenReturn(Optional.of(List.of("오징어볶음", "계란찜")));

        assertThat(finder.findBroad(MEMBER_ID, "술안주")).containsExactly("오징어볶음", "계란찜");
        verify(generator, never()).generate(any(), any(), any());
    }

    @Test
    @DisplayName("넓은 요청 캐시가 없으면 넓은 요청 프롬프트로 AI를 부르고 정리한 값을 넓은 요청 종류로 저장한다")
    void callsBroadAiAndSaves_whenNotCached() {
        when(generator.generate(MEMBER_ID, DishListKind.BROAD, "술안주")).thenReturn(
                List.of("오징어볶음", "감자튀김", " 감자 튀김 ", "계란찜", "닭꼬치", "골뱅이무침", "두부김치"));

        List<String> dishes = finder.findBroad(MEMBER_ID, "술안주");

        assertThat(dishes).containsExactly("오징어볶음", "감자튀김", "계란찜", "닭꼬치", "골뱅이무침");
        verify(store).save(MEMBER_ID, DishListKind.BROAD, "술안주", dishes);
        verify(generator, never()).generate(MEMBER_ID, DishListKind.SIMILAR, "술안주");
    }

    @Test
    @DisplayName("넓은 요청 AI가 빈 목록을 내면 빈 목록을 돌려주고, 저장은 가게에 맡긴다(가게가 빈 목록을 버린다)")
    void returnsEmpty_whenBroadAiReturnsNothing() {
        when(generator.generate(MEMBER_ID, DishListKind.BROAD, "고마워")).thenReturn(List.of());

        assertThat(finder.findBroad(MEMBER_ID, "고마워")).isEmpty();
        verify(store).save(MEMBER_ID, DishListKind.BROAD, "고마워", List.of());
    }

    @Test
    @DisplayName("넓은 요청 AI가 실패하면 빈 목록으로 진행하고 저장하지 않는다")
    void returnsEmpty_whenBroadAiFails() {
        when(generator.generate(any(), any(), any())).thenThrow(new IllegalStateException("timeout"));

        assertThat(finder.findBroad(MEMBER_ID, "술안주")).isEmpty();
        verify(store, never()).save(any(), any(), any(), any());
    }
}
