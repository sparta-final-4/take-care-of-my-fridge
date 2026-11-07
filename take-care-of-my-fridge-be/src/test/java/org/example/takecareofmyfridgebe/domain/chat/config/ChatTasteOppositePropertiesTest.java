package org.example.takecareofmyfridgebe.domain.chat.config;

import org.example.takecareofmyfridgebe.domain.member.entity.Member.Taste;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatTasteOppositePropertiesTest {

    private final ChatTasteOppositeProperties opposites = new ChatTasteOppositeProperties(List.of(
            List.of(Taste.MILD, Taste.SPICY), List.of(Taste.MILD, Taste.SALTY)));

    @Test
    @DisplayName("반대 쌍은 양쪽으로 보고, 단맛·감칠맛은 반대가 없다")
    void returnsOppositesBothWays() {
        assertThat(opposites.oppositesOf(Taste.MILD)).containsExactlyInAnyOrder(Taste.SPICY, Taste.SALTY);
        assertThat(opposites.oppositesOf(Taste.SPICY)).containsExactly(Taste.MILD);
        assertThat(opposites.oppositesOf(Taste.SALTY)).containsExactly(Taste.MILD);
        assertThat(opposites.oppositesOf(Taste.SWEET)).isEmpty();
        assertThat(opposites.oppositesOf(Taste.UMAMI)).isEmpty();
    }
}
