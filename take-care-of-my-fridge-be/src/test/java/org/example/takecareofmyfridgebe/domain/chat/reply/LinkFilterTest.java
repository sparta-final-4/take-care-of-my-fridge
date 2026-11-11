package org.example.takecareofmyfridgebe.domain.chat.reply;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LinkFilterTest {

    // 조각을 차례로 넣고 보낸 글을 모두 이어 붙인다
    private static String filter(String... chunks) {
        LinkFilter filter = new LinkFilter();
        StringBuilder out = new StringBuilder();
        for (String chunk : chunks) {
            out.append(filter.feed(chunk));
        }
        return out.append(filter.finish()).toString();
    }

    @Test
    @DisplayName("조각 경계에서 쪼개진 링크(https:/ + /naver.com)도 이어 붙여 지운다")
    void removesLinkSplitAcrossChunks() {
        assertThat(filter("자세한 건 https:/", "/naver.com 에서 보세요")).isEqualTo("자세한 건 에서 보세요");
        assertThat(filter("참고: htt", "ps://blog.", "naver.com/abc?x=1&y=2 입니다")).isEqualTo("참고: 입니다");
    }

    @Test
    @DisplayName("앞 조각이 공백으로 끝나고 다음 조각이 링크로 시작해도 지운 뒤 공백이 겹치지 않는다")
    void doesNotDoubleSpace_whenLinkStartsNextChunk() {
        assertThat(filter("참고 ", "https://a.com 에서 보세요")).isEqualTo("참고 에서 보세요");
    }

    @Test
    @DisplayName("문장 끝 마침표·물음표와 숫자는 모아 두지 않고 바로 보낸다")
    void sendsPunctuationAndNumbersImmediately() {
        LinkFilter filter = new LinkFilter();

        assertThat(filter.feed("볶아요.")).isEqualTo("볶아요.");
        assertThat(filter.feed(" 1.5")).isEqualTo(" 1.5");
        assertThat(filter.feed(" 드릴까요?")).isEqualTo(" 드릴까요?");
    }

    @Test
    @DisplayName("www.로 시작하는 링크와 도메인 형태(naver.com/abc, a.co.kr)를 지운다")
    void removesWwwAndDomainLinks() {
        assertThat(filter("www.example.com 에 레시피가 있어요")).isEqualTo(" 에 레시피가 있어요");
        assertThat(filter("레시피는 naver.com/abc 를 참고하세요.")).isEqualTo("레시피는 를 참고하세요.");
        assertThat(filter("http://a.co.kr/x?y=1 여기요")).isEqualTo(" 여기요");
        assertThat(filter("여기https://a.com 참고")).isEqualTo("여기 참고");
    }

    @Test
    @DisplayName("링크만 있던 괄호는 괄호째 지운다")
    void removesEmptyParenthesesAfterLink() {
        assertThat(filter("두부조림(https://a.com)을 추천해요")).isEqualTo("두부조림을 추천해요");
    }

    @Test
    @DisplayName("숫자·단위·문장 끝 마침표 뒤 이어지는 말·영어 낱말은 지우지 않는다 (1.5배, 3.5cm, 약 2.5kg)")
    void keepsNumbersUnitsAndSentences() {
        for (String text : List.of("물을 1.5배 넣어요", "3.5cm 두께로 썰어요", "약 2.5kg 고기", "좋아요.다음 단계는 볶기예요",
                "tip: 약불로 졸여요", "[소개] 두부 요리예요.\n[1] 짭짤해요.", "1. 두부를 부쳐요.\n2. 간장을 넣어요.", "Step 1.5")) {
            assertThat(filter(text)).as(text).isEqualTo(text);
        }
    }

    @Test
    @DisplayName("숫자·단위를 조각 경계에서 나눠 받아도 그대로 이어 보낸다")
    void keepsNumbersSplitAcrossChunks() {
        assertThat(filter("물을 1.", "5배 넣고 3.", "5cm로 썰어요.")).isEqualTo("물을 1.5배 넣고 3.5cm로 썰어요.");
    }

    @Test
    @DisplayName("한글 조각은 모아 두지 않고 바로 보낸다")
    void sendsKoreanImmediately() {
        LinkFilter filter = new LinkFilter();

        assertThat(filter.feed("두부를")).isEqualTo("두부를");
        assertThat(filter.feed(" 부쳐요")).isEqualTo(" 부쳐요");
    }

    @Test
    @DisplayName("링크만 있던 답은 빈 글이 된다")
    void returnsEmpty_whenOnlyLink() {
        assertThat(filter("https://naver.com")).isEmpty();
    }

    @Test
    @DisplayName("링크 글자가 너무 길게 이어지면(300자 넘음) 링크가 아닌 것으로 보고 모아 두지 않는다")
    void sendsLongAsciiRun_whenOverMaxHeld() {
        LinkFilter filter = new LinkFilter();
        String longRun = "a".repeat(LinkFilter.MAX_HELD + 1);

        assertThat(filter.feed(longRun)).isEqualTo(longRun);
    }
}
