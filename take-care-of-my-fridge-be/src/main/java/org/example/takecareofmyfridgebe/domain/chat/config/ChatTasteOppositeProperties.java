package org.example.takecareofmyfridgebe.domain.chat.config;

import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 서로 반대인 맛. 다시 추천에서 이번 말에 원한 맛과 반대인 맛은 원하는 맛에서 지운다 ("짭짤한 거" → "담백한 거로"면 짠맛을 지움).
 * 반대가 아닌 맛은 쌓는다 ("매운 걸로" → "단 것도" → 매운맛 + 단맛). 쌍은 양쪽으로 본다.
 *
 * @param pairs 반대인 두 맛 (예: [MILD, SPICY], [MILD, SALTY]). 단맛·감칠맛은 반대가 없다
 */
@ConfigurationProperties(prefix = "app.chat.taste-opposites")
public record ChatTasteOppositeProperties(
        List<List<Member.Taste>> pairs
) {

    public ChatTasteOppositeProperties {
        pairs = pairs == null ? List.of() : pairs.stream().map(List::copyOf).toList();
    }

    // 그 맛과 반대인 맛
    public Set<Member.Taste> oppositesOf(Member.Taste taste) {
        Set<Member.Taste> result = EnumSet.noneOf(Member.Taste.class);
        for (List<Member.Taste> pair : pairs) {
            if (pair.size() == 2 && pair.contains(taste)) {
                pair.stream().filter(other -> other != taste).forEach(result::add);
            }
        }
        return result;
    }
}
