package org.example.takecareofmyfridgebe.domain.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * 알레르기 재료를 찾는 말 (AllergyWordChecker, AllergyExclusionFinder).
 * "○○ 알레르기", "○○ 못 먹어", "○○ 먹으면 안 돼"처럼 말 바로 앞 낱말을 ○○로 보고, 묶음 말이거나 재료 사전에 있는 이름일 때만 뺄 재료로 쓴다.
 *
 * @param markers     ○○ 뒤에 오는 말 (글자 사이 공백은 무시: "못먹" = "못 먹")
 * @param particles   ○○ 끝에서 떼어 보는 조사 ("새우를" → "새우"). 뗀 이름보다 붙은 이름("오이")을 먼저 본다
 * @param notSameKind 이름에 알레르기 재료 이름이 들어 있어도 다른 재료라 넓히지 않을 이름 ("땅콩호박", "바게트", "스파게티", "멍게살")
 */
@ConfigurationProperties(prefix = "app.chat.allergy-words")
public record ChatAllergyWordProperties(
        @DefaultValue({"알레르기", "알러지", "못먹", "먹으면안"})
        List<String> markers,
        @DefaultValue({"에는", "에도", "은", "는", "이", "가", "을", "를", "에", "도"})
        List<String> particles,
        @DefaultValue({"땅콩호박", "바게트", "스파게티", "멍게살"})
        List<String> notSameKind
) {
}
