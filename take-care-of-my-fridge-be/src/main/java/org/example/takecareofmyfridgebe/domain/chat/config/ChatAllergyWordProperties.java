package org.example.takecareofmyfridgebe.domain.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 알레르기 재료를 찾는 말 (AllergyWordChecker, AllergyExclusionFinder).
 * "○○ 알레르기", "○○ 못 먹어", "○○ 먹으면 안 돼"처럼 말 바로 앞 낱말을 ○○로 보고, 묶음 말이거나 재료 사전에 있는 이름일 때만 뺄 재료로 쓴다.
 *
 * @param markers     ○○ 뒤에 오는 말 (글자 사이 공백은 무시: "못먹" = "못 먹")
 * @param particles   ○○ 끝에서 떼어 보는 조사 ("새우를" → "새우"). 뗀 이름보다 붙은 이름("오이")을 먼저 본다
 * @param notSameKind 재료마다 이름에 그 재료 이름이 들어 있거나(두 글자 이상) 그 글자로 시작해도(한 글자) 다른 재료라 넓히지 않을 이름
 *                    ("땅콩" → 땅콩호박, "굴" → 굴비, "마" → 마늘). 그 재료를 넓힐 때만 본다 ("마늘 알레르기"면 마늘가루는 뺀다)
 */
@ConfigurationProperties(prefix = "app.chat.allergy-words")
public record ChatAllergyWordProperties(
        @DefaultValue({"알레르기", "알러지", "못먹", "먹으면안"})
        List<String> markers,
        @DefaultValue({"에는", "에도", "은", "는", "이", "가", "을", "를", "에", "도"})
        List<String> particles,
        List<NotSameKind> notSameKind
) {

    public ChatAllergyWordProperties {
        notSameKind = notSameKind == null ? List.of() : List.copyOf(notSameKind);
    }

    // 그 재료를 넓힐 때 빼는 이름 (공백 뺀 이름)
    public Set<String> notSameKindOf(String word) {
        String key = compact(word);
        return notSameKind.stream()
                .filter(entry -> compact(entry.word()).equals(key))
                .flatMap(entry -> entry.names().stream())
                .map(ChatAllergyWordProperties::compact)
                .collect(Collectors.toUnmodifiableSet());
    }

    // 모든 재료의 넓히지 않을 이름 (공백 뺀 이름). 밀키트 상품 이름을 볼 때 쓴다
    public Set<String> allNotSameKindNames() {
        return notSameKind.stream()
                .flatMap(entry -> entry.names().stream())
                .map(ChatAllergyWordProperties::compact)
                .filter(name -> !name.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String compact(String text) {
        return text == null ? "" : text.replaceAll("\\s+", "");
    }

    /**
     * @param word  알레르기 재료 이름 ("굴")
     * @param names 그 재료를 넓힐 때 빼는 재료 사전 이름 ("굴비")
     */
    public record NotSameKind(String word, List<String> names) {

        public NotSameKind {
            names = names == null ? List.of() : List.copyOf(names);
        }
    }
}
