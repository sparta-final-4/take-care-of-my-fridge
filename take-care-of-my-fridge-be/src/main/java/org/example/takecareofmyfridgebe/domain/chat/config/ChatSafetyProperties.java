package org.example.takecareofmyfridgebe.domain.chat.config;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * AI 상담 안전 장치 설정 (config/chat-safety.yml). 금지어·자해·탈취 단어, 건강 말, 알레르기 말과 묶음을 중첩 record로 둔다.
 *
 * @param words         AI를 부르기 전에 거르는 단어 (SafetyWordChecker)
 * @param health        건강 관련 말 (HealthWordChecker)
 * @param allergy       알레르기 재료를 찾는 말 (AllergyWordChecker, AllergyExclusionFinder)
 * @param allergyGroups 알레르기 묶음 말과 재료 (AllergyExclusionFinder)
 */
@ConfigurationProperties(prefix = "app.chat.safety")
public record ChatSafetyProperties(
        Words words,
        Health health,
        Allergy allergy,
        List<AllergyGroup> allergyGroups
) {
    public ChatSafetyProperties {
        allergyGroups = allergyGroups == null ? List.of() : List.copyOf(allergyGroups);
    }

    /**
     * AI를 부르기 전에 거르는 단어 (SafetyWordChecker). 비교는 소문자로 바꾸고 공백·숫자·기호를 뺀 글로 한다 ("시1발" → "시발").
     * 제외 목록에 있는 말은 먼저 지우고 본다 ("마약김밥", "폭탄주", "죽 먹고"는 걸리지 않는다).
     *
     * @param banned     욕설·불법·위험한 말
     * @param selfHarm   자해·자살을 말하는 말. 금지어보다 먼저 보고 안내를 다르게 한다
     * @param injection  지시를 무시하라거나 프롬프트를 공개하라는 말. 역할만 정하는 말("너는 이제부터 ~야")은 넣지 않는다
     * @param exclusions 걸리는 말이 들어 있어도 음식 이름 등이라 지우고 보는 말
     */
    public record Words(
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

    /**
     * 건강 관련 말 (HealthWordChecker). 사용자가 보낸 말에 있으면 답 끝에 서버가 상담 안내를 붙인다 (막지 않고 하루 횟수도 센다).
     * 비교는 공백을 뺀 글로 하고, 제외 목록에 있는 말은 먼저 지우고 본다 ("약불", "약간"은 걸리지 않는다).
     * "약"·"신장" 한 글자·두 글자는 넣지 않는다 ("약 10분", "신장 170cm"). 다이어트는 생활 요청이라 넣지 않는다.
     *
     * @param health     질환·알레르기·임신·약을 말하는 말
     * @param exclusions 건강 말이 들어 있어도 요리 말 등이라 지우고 보는 말
     */
    public record Health(
            @DefaultValue({"당뇨", "혈당", "고혈압", "혈압", "고지혈", "콜레스테롤", "신장질환", "신장병", "신장기능", "콩팥", "투석",
                    "통풍", "요산", "질환", "환자", "위염", "역류성", "항암", "알레르기", "알러지", "아토피", "임신", "임산부", "산모",
                    "수유", "모유", "복용", "처방", "약먹", "약을먹", "약드시"})
            List<String> health,
            @DefaultValue({"약불", "약간", "약한불", "신장개업", "수유역"})
            List<String> exclusions
    ) {
    }

    /**
     * 알레르기 재료를 찾는 말 (AllergyWordChecker, AllergyExclusionFinder).
     * "○○ 알레르기", "○○ 못 먹어", "○○ 먹으면 안 돼"처럼 말 바로 앞 낱말을 ○○로 보고, 묶음 말이거나 재료 사전에 있는 이름일 때만 뺄 재료로 쓴다.
     *
     * @param markers     ○○ 뒤에 오는 말 (글자 사이 공백은 무시: "못먹" = "못 먹")
     * @param particles   ○○ 끝에서 떼어 보는 조사 ("새우를" → "새우"). 뗀 이름보다 붙은 이름("오이")을 먼저 본다
     * @param notSameKind 재료마다 이름에 그 재료 이름이 들어 있거나(두 글자 이상) 그 글자로 시작해도(한 글자) 다른 재료라 넓히지 않을 이름
     *                    ("땅콩" → 땅콩호박, "굴" → 굴비, "마" → 마늘). 그 재료를 넓힐 때만 본다 ("마늘 알레르기"면 마늘가루는 뺀다)
     */
    public record Allergy(
            @DefaultValue({"알레르기", "알러지", "못먹", "먹으면안"})
            List<String> markers,
            @DefaultValue({"에는", "에도", "은", "는", "이", "가", "을", "를", "에", "도"})
            List<String> particles,
            List<NotSameKind> notSameKind
    ) {
        public Allergy {
            notSameKind = notSameKind == null ? List.of() : List.copyOf(notSameKind);
        }

        // 그 재료를 넓힐 때 빼는 이름 (공백 뺀 이름)
        public Set<String> notSameKindOf(String word) {
            String key = compact(word);
            return notSameKind.stream()
                    .filter(entry -> compact(entry.word()).equals(key))
                    .flatMap(entry -> entry.names().stream())
                    .map(Allergy::compact)
                    .collect(Collectors.toUnmodifiableSet());
        }

        // 모든 재료의 넓히지 않을 이름 (공백 뺀 이름). 밀키트 상품 이름을 볼 때 쓴다
        public Set<String> allNotSameKindNames() {
            return notSameKind.stream()
                    .flatMap(entry -> entry.names().stream())
                    .map(Allergy::compact)
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

    /**
     * 알레르기 묶음 말 ("견과류 알레르기" → 호두·아몬드·잣·캐슈넛·피스타치오·땅콩). 재료는 실제 재료 사전 이름으로 쓴다.
     * 묶음의 재료도 이름에 들어 있는 재료로 넓힌다 (땅콩 → 땅콩버터). 재료 사전에 없는 이름은 무시한다.
     *
     * @param word        묶음 말 ("견과류", "계란")
     * @param ingredients 그 말이 뜻하는 재료 사전 이름 ("호두", "아몬드", …)
     */
    public record AllergyGroup(String word, List<String> ingredients) {
        public AllergyGroup {
            ingredients = ingredients == null ? List.of() : List.copyOf(ingredients);
        }
    }
}
