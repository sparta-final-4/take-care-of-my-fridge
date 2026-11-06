package org.example.takecareofmyfridgebe.domain.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 분류 이름과 다른 흔한 말을 분류 묶음으로 본다 (CategoryWordChecker). 예: "국물" → 국·찌개·전골·수프·스튜.
 * 추천·다시 추천에서 분류 단어 보정에 쓴다. 묶음의 분류는 실제 레시피 분류 이름이어야 하고, 없는 분류는 무시한다.
 *
 * @param groups 묶음 말과 그 말이 뜻하는 분류
 */
@ConfigurationProperties(prefix = "app.chat.category-words")
public record ChatCategoryWordProperties(
        List<Group> groups
) {

    public ChatCategoryWordProperties {
        groups = groups == null ? List.of() : List.copyOf(groups);
    }

    /**
     * @param word       묶음 말 ("국물")
     * @param categories 그 말이 뜻하는 분류 ("국", "찌개", …)
     */
    public record Group(String word, List<String> categories) {

        public Group {
            categories = categories == null ? List.of() : List.copyOf(categories);
        }
    }
}
