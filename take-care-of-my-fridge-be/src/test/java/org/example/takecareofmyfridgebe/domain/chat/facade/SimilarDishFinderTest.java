package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.ai.SimilarDishGenerator;
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
        when(store.find(any(), any())).thenReturn(Optional.empty());
        finder = new SimilarDishFinder(store, generator);
    }

    @Test
    @DisplayName("캐시가 있으면 AI를 부르지 않고 캐시 값을 쓴다")
    void usesCache_withoutCallingAi() {
        when(store.find(MEMBER_ID, "김피탕")).thenReturn(Optional.of(List.of("탕수육", "깐풍기")));

        assertThat(finder.find(MEMBER_ID, "김피탕")).containsExactly("탕수육", "깐풍기");
        verify(generator, never()).generate(any(), any());
    }

    @Test
    @DisplayName("캐시가 없으면 AI를 한 번 부르고, 요리 이름 자신·빈 이름·띄어쓰기만 다른 중복을 빼고 5개까지 저장한다")
    void callsAiAndSavesCleaned_whenNotCached() {
        when(generator.generate(MEMBER_ID, "나가사키 짬뽕")).thenReturn(
                List.of("나가사키짬뽕", " 짬뽕 ", "", "짬뽕", "짬뽕밥", "해물짬뽕", "우동", "기스면", "울면"));

        List<String> dishes = finder.find(MEMBER_ID, "나가사키 짬뽕");

        assertThat(dishes).containsExactly("짬뽕", "짬뽕밥", "해물짬뽕", "우동", "기스면");
        verify(store).save(MEMBER_ID, "나가사키 짬뽕", dishes);
    }

    @Test
    @DisplayName("AI 호출이 실패하면 예외를 내지 않고 빈 목록으로 진행하며 캐시에 저장하지 않는다")
    void returnsEmpty_whenAiFails() {
        when(generator.generate(any(), any())).thenThrow(new IllegalStateException("timeout"));

        assertThat(finder.find(MEMBER_ID, "김피탕")).isEmpty();
        verify(store, never()).save(any(), any(), any());
    }

    @Test
    @DisplayName("요리 이름이 없으면 캐시도 AI도 보지 않는다")
    void skips_whenNoDish() {
        assertThat(finder.find(MEMBER_ID, " ")).isEmpty();
        verify(store, never()).find(any(), any());
        verify(generator, never()).generate(any(), any());
    }
}
