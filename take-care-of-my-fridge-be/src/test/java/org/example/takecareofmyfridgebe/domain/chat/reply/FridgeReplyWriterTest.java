package org.example.takecareofmyfridgebe.domain.chat.reply;

import org.example.takecareofmyfridgebe.domain.chat.dto.FridgeScope;
import org.example.takecareofmyfridgebe.domain.chat.reply.FridgeReplyWriter.Line;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class FridgeReplyWriterTest {

    @Test
    @DisplayName("남은 줄은 D-3 이내만 남은 날을 붙여 쓰고, 지난 줄은 뒤에 따로 쓴다")
    void writesAliveWithLabelsAndExpiredAfter() {
        String reply = FridgeReplyWriter.write(FridgeScope.MEALKIT, List.of(
                new Line("우유", -2),
                new Line("두부", 1),
                new Line("대파", 3),
                new Line("된장국 밀키트", 10)), List.of(), false);

        assertThat(reply).isEqualTo("냉장고에 두부(내일까지), 대파(3일 남음), 된장국 밀키트가 있어요. 우유는 지났어요.");
    }

    @Test
    @DisplayName("오늘까지면 \"오늘까지\", 받침 있는 마지막 이름에는 \"이\"를 붙인다")
    void writesTodayLabelAndParticle() {
        String reply = FridgeReplyWriter.write(FridgeScope.IMMINENT, List.of(new Line("양파", 5), new Line("버섯", 0)), List.of(), false);

        assertThat(reply).isEqualTo("냉장고에 양파, 버섯(오늘까지)이 있어요.");
    }

    @Test
    @DisplayName("남은 줄은 6개, 지난 줄은 3개까지 쓰고 나머지는 \"외 N개\"로 센다. 같은 이름은 한 번만 쓴다")
    void cutsLongListsWithRestCount() {
        List<Line> lines = new ArrayList<>(IntStream.rangeClosed(1, 9).mapToObj(i -> new Line("재료" + i, 10)).toList());
        lines.add(new Line("재료1", 12));
        IntStream.rangeClosed(1, 5).forEach(i -> lines.add(new Line("지난것" + i, -1)));

        String reply = FridgeReplyWriter.write(FridgeScope.IMMINENT, lines, List.of(), false);

        assertThat(reply).isEqualTo("냉장고에 재료1, 재료2, 재료3, 재료4, 재료5, 재료6 외 3개가 있어요. 지난것1, 지난것2, 지난것3 외 2개는 지났어요.");
    }

    @Test
    @DisplayName("전체 보기는 가짓수를 먼저 말하고, D-3 이내만 앞 3개 + \"외 N개\"로 써서 임박 보기와 다르게 보인다")
    void writesAllScopeWithCountAndSoonOnly() {
        List<Line> lines = List.of(
                new Line("양파", -5), new Line("깻잎", -3), new Line("고추", -3), new Line("배추", -1), new Line("버섯", -2),
                new Line("대파", 0), new Line("버섯", 0), new Line("서울우유", 1), new Line("표고버섯", 1), new Line("우유", 2),
                new Line("감자", 15), new Line("마늘", 21));

        String all = FridgeReplyWriter.write(FridgeScope.ALL, lines, List.of(), false);
        String imminent = FridgeReplyWriter.write(FridgeScope.IMMINENT,
                lines.stream().filter(line -> line.dDay() <= 3).toList(), List.of(), false);

        assertThat(all).isEqualTo("냉장고에 재료 7가지가 있어요. 곧 끝나는 건 대파(오늘까지), 버섯(오늘까지), 서울우유(내일까지) 외 2개예요. "
                + "양파, 깻잎, 고추 외 2개는 지났어요.");
        assertThat(imminent).startsWith("냉장고에 대파(오늘까지), 버섯(오늘까지), 서울우유(내일까지), 표고버섯(내일까지), 우유(모레까지)가 있어요.");
        assertThat(all).isNotEqualTo(imminent);
    }

    @Test
    @DisplayName("전체 보기에서 곧 끝나는 재료가 없으면 없다고 쓰고, 받침 있는 마지막 이름에는 \"이에요\"를 붙인다")
    void writesAllScopeWithoutSoon() {
        assertThat(FridgeReplyWriter.write(FridgeScope.ALL, List.of(new Line("감자", 15)), List.of(), false))
                .isEqualTo("냉장고에 재료 1가지가 있어요. 소비기한이 곧 끝나는 재료는 없어요.");
        assertThat(FridgeReplyWriter.write(FridgeScope.ALL, List.of(new Line("버섯", 1)), List.of(), false))
                .isEqualTo("냉장고에 재료 1가지가 있어요. 곧 끝나는 건 버섯(내일까지)이에요.");
    }

    @Test
    @DisplayName("말한 재료가 없으면 \"○○는 지금 냉장고에 없어요\"를 쓴다")
    void writesMissing() {
        assertThat(FridgeReplyWriter.write(FridgeScope.ALL, List.of(), List.of("두부"), true))
                .isEqualTo("두부는 지금 냉장고에 없어요.");
        assertThat(FridgeReplyWriter.write(FridgeScope.ALL, List.of(new Line("두부", 2)), List.of("당근"), true))
                .isEqualTo("냉장고에 두부(모레까지)가 있어요. 당근은 지금 냉장고에 없어요.");
    }

    @Test
    @DisplayName("보여 줄 줄이 없으면 범위마다 안내 글을 쓴다")
    void writesEmptyReplyByScope() {
        assertThat(FridgeReplyWriter.write(FridgeScope.ALL, List.of(), List.of(), false)).isEqualTo("냉장고에 등록된 재료가 없어요.");
        assertThat(FridgeReplyWriter.write(FridgeScope.MEALKIT, List.of(), List.of(), false)).isEqualTo("냉장고에 밀키트가 없어요.");
        assertThat(FridgeReplyWriter.write(FridgeScope.IMMINENT, List.of(), List.of(), false))
                .isEqualTo("소비기한이 곧 끝나는 재료는 없어요.");
    }
}
