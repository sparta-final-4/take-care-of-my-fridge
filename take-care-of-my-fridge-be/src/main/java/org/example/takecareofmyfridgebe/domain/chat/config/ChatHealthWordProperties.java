package org.example.takecareofmyfridgebe.domain.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * 건강 관련 말 (HealthWordChecker). 사용자가 보낸 말에 있으면 답 끝에 서버가 상담 안내를 붙인다 (막지 않고 하루 횟수도 센다).
 * 비교는 공백을 뺀 글로 하고, 제외 목록에 있는 말은 먼저 지우고 본다 ("약불", "약간"은 걸리지 않는다).
 * "약"·"신장" 한 글자·두 글자는 넣지 않는다 ("약 10분", "신장 170cm"). 다이어트는 생활 요청이라 넣지 않는다.
 *
 * @param health     질환·알레르기·임신·약을 말하는 말
 * @param exclusions 건강 말이 들어 있어도 요리 말 등이라 지우고 보는 말
 */
@ConfigurationProperties(prefix = "app.chat.health-words")
public record ChatHealthWordProperties(
        @DefaultValue({"당뇨", "혈당", "고혈압", "혈압", "고지혈", "콜레스테롤", "신장질환", "신장병", "신장기능", "콩팥", "투석",
                "통풍", "요산", "질환", "환자", "위염", "역류성", "항암", "알레르기", "알러지", "아토피", "임신", "임산부", "산모",
                "수유", "모유", "복용", "처방", "약먹", "약을먹", "약드시"})
        List<String> health,
        @DefaultValue({"약불", "약간", "약한불", "신장개업", "수유역"})
        List<String> exclusions
) {
}
