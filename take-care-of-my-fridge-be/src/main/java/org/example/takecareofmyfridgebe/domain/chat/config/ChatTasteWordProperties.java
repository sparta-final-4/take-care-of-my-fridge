package org.example.takecareofmyfridgebe.domain.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * 메시지의 맛 단어로 AI가 뽑은 원하는 맛·피할 맛을 한 번 더 확인할 때 쓰는 단어 (TasteWordChecker).
 * 비교는 공백을 뺀 글로 한다.
 *
 * @param spicy          매운맛 단어 ("좀 더 매운 걸로", "칼칼한 거")
 * @param sweet          단맛 단어 ("달달한 거", "단 거")
 * @param negationBefore 맛 단어 바로 앞에 오면 피하는 말 ("안 맵게", "덜 달게")
 * @param negationAfter  맛 단어 뒤 몇 글자 안에서 시작하면 피하는 말 ("매운 거 말고", "맵지 않게", "단 거 싫어", "매운 건 빼줘", "매운 게 안 들어간")
 * @param salty          짠맛 단어
 * @param umami          감칠맛 단어
 * @param mild           순한맛 단어
 * @param wantBefore     맛 단어 바로 앞에 오면 그 맛을 확실히 원하는 말 ("좀 더 매운")
 * @param wantAfter      맛 단어 뒤 몇 글자 안에서 시작하면 그 맛을 확실히 원하는 말 ("매운 걸로", "매운 거 먹고 싶어").
 *                       부정어도 이 말도 없으면 서버 판단이 애매하므로 AI가 피할 맛으로 둔 맛을 원하는 맛으로 바꾸지 않는다
 * @param clear          맛 조건을 지우라는 말 ("맛 상관없이", "맛은 아무거나"). 이 말이 없고 피하는 맛만 말했으면
 *                       AI가 넣은 뺄 조건 TASTE를 서버가 지운다 ("매운 건 빼줘"가 직전에 원한 짠맛까지 지우지 않게)
 */
@ConfigurationProperties(prefix = "app.chat.taste-words")
public record ChatTasteWordProperties(
        @DefaultValue({"매운", "맵게", "맵지", "매콤", "얼큰", "칼칼"})
        List<String> spicy,
        @DefaultValue({"달달", "달게", "단거", "단걸", "단것", "달콤", "단맛"})
        List<String> sweet,
        @DefaultValue({"안", "덜"})
        List<String> negationBefore,
        @DefaultValue({"말고", "빼고", "않", "싫", "없이", "못먹", "빼줘", "빼주", "빼달", "제외", "안들어간", "없는"})
        List<String> negationAfter,
        @DefaultValue({"짠", "짭짤", "짜게", "짭조름"})
        List<String> salty,
        @DefaultValue({"감칠", "고소"})
        List<String> umami,
        @DefaultValue({"순한", "담백", "슴슴", "심심", "자극적이지"})
        List<String> mild,
        @DefaultValue({"더"})
        List<String> wantBefore,
        @DefaultValue({"걸로", "거로", "것으로", "먹고싶", "땡", "좋아", "원해", "당겨"})
        List<String> wantAfter,
        @DefaultValue({"맛상관없", "맛은상관없", "맛아무거나", "맛은아무거나"})
        List<String> clear
) {
}
