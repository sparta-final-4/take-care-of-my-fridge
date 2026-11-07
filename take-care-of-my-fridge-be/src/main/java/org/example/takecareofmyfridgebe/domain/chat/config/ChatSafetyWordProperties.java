package org.example.takecareofmyfridgebe.domain.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * AI를 부르기 전에 거르는 단어 (SafetyWordChecker). 비교는 소문자로 바꾸고 공백·숫자·기호를 뺀 글로 한다 ("시1발" → "시발").
 * 제외 목록에 있는 말은 먼저 지우고 본다 ("마약김밥", "폭탄주", "죽 먹고"는 걸리지 않는다).
 *
 * @param banned     욕설·불법·위험한 말
 * @param selfHarm   자해·자살을 말하는 말. 금지어보다 먼저 보고 안내를 다르게 한다
 * @param injection  지시를 무시하라거나 프롬프트를 공개하라는 말. 역할만 정하는 말("너는 이제부터 ~야")은 넣지 않는다
 * @param exclusions 걸리는 말이 들어 있어도 음식 이름 등이라 지우고 보는 말
 */
@ConfigurationProperties(prefix = "app.chat.safety-words")
public record ChatSafetyWordProperties(
        @DefaultValue({"씨발", "시발", "씨바", "ㅅㅂ", "ㅆㅂ", "개새끼", "개새기", "병신", "ㅂㅅ", "좆", "존나", "ㅈㄴ",
                "미친놈", "미친년", "닥쳐", "폭탄", "마약", "필로폰", "대마초", "살인", "사람죽이", "독극물", "독약", "총기", "해킹"})
        List<String> banned,
        @DefaultValue({"죽고싶", "죽어버리고싶", "자살", "자해", "극단적선택", "목숨을끊", "살기싫", "사라지고싶", "손목긋",
                "뛰어내리고싶"})
        List<String> selfHarm,
        @DefaultValue({"지시무시", "지시를무시", "지시는무시", "지시사항무시", "명령무시", "명령을무시", "시스템프롬프트",
                "프롬프트보여", "프롬프트를보여", "프롬프트알려", "프롬프트를알려", "프롬프트공개", "프롬프트를공개", "프롬프트출력",
                "지시보여", "지시를보여", "지시알려", "지시를알려", "ignoreprevious", "ignoreallprevious", "ignoretheabove",
                "ignoreyourinstructions", "systemprompt", "developermode", "개발자모드", "jailbreak", "탈옥"})
        List<String> injection,
        @DefaultValue({"죽먹고", "마약김밥", "마약계란장", "마약계란", "마약달걀", "마약떡볶이", "마약옥수수", "마약소스", "마약토스트",
                "폭탄주", "폭탄계란찜", "폭탄달걀찜", "폭탄주먹밥", "칼국수", "독일", "약불", "약간"})
        List<String> exclusions
) {
}
