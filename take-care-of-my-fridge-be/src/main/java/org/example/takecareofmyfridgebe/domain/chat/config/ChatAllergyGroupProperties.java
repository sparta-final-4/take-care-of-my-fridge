package org.example.takecareofmyfridgebe.domain.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 알레르기 묶음 말 ("견과류 알레르기" → 호두·아몬드·잣·캐슈넛·피스타치오·땅콩). 재료는 실제 재료 사전 이름으로 쓴다.
 * 묶음의 재료도 이름에 들어 있는 재료로 넓힌다 (땅콩 → 땅콩버터). 재료 사전에 없는 이름은 무시한다.
 *
 * @param groups 묶음 말과 그 말이 뜻하는 재료
 */
@ConfigurationProperties(prefix = "app.chat.allergy-groups")
public record ChatAllergyGroupProperties(
        List<Group> groups
) {

    public ChatAllergyGroupProperties {
        groups = groups == null ? List.of() : List.copyOf(groups);
    }

    /**
     * @param word        묶음 말 ("견과류", "계란")
     * @param ingredients 그 말이 뜻하는 재료 사전 이름 ("호두", "아몬드", …)
     */
    public record Group(String word, List<String> ingredients) {

        public Group {
            ingredients = ingredients == null ? List.of() : List.copyOf(ingredients);
        }
    }
}
