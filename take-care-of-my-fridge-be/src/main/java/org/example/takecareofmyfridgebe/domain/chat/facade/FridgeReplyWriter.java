package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.dto.FridgeScope;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 냉장고 보기 답 글. 스프링에 의존하지 않는 순수 클래스. 답 생성 AI 없이 이 글을 그대로 보내고 저장한다.
 * <pre>
 * (전체) 냉장고에 재료 12가지가 있어요. 곧 끝나는 건 대파(오늘까지), 버섯(오늘까지), 서울우유(내일까지) 외 6개예요. 양파, 깻잎, 고추 외 4개는 지났어요.
 * (임박·밀키트·이름) 냉장고에 두부(내일까지), 대파(3일 남음), 된장국 밀키트가 있어요. 우유는 지났어요.
 * 두부는 지금 냉장고에 없어요.
 * </pre>
 * <ul>
 *   <li>전체 보기는 가짓수를 먼저 말하고, D-3 이내만 앞 {@value #SOON_SHOWN}개 + "외 N개"로 쓴다 (임박 보기와 같은 답처럼 보이지 않게)</li>
 *   <li>남은 줄은 앞 {@value #ALIVE_SHOWN}개, 지난 줄은 앞 {@value #EXPIRED_SHOWN}개까지 쓰고 나머지는 "외 N개"로 센다</li>
 *   <li>같은 이름은 한 번만 쓴다 (소비기한이 가까운 줄 기준). 남은 날은 D-3 이내만 쓴다 (오늘까지·내일까지·모레까지·3일 남음)</li>
 *   <li>이/가·은/는은 마지막 말의 받침으로 고른다</li>
 * </ul>
 */
public final class FridgeReplyWriter {

    static final int ALIVE_SHOWN = 6;
    static final int EXPIRED_SHOWN = 3;
    // 전체 보기에서 "곧 끝나는 건"에 쓸 이름 수
    static final int SOON_SHOWN = 3;
    private static final int LABEL_DAYS = 3;

    private FridgeReplyWriter() {
    }

    /**
     * @param name 보여 줄 이름
     * @param dDay 소비기한까지 남은 일수 (음수면 지남)
     */
    public record Line(String name, long dDay) {
    }

    /**
     * @param scope   범위 (보여 줄 줄이 없을 때 안내 글을 고른다)
     * @param lines   보여 줄 줄 (소비기한 순). 지난 줄이 섞여 있어도 된다
     * @param missing 말했지만 냉장고에 없는 재료 이름 (말한 그대로)
     * @param named   재료 이름을 말했는지 (말했으면 범위 안내 글을 쓰지 않는다)
     */
    public static String write(FridgeScope scope, List<Line> lines, List<String> missing, boolean named) {
        Map<String, Long> alive = new LinkedHashMap<>();
        Map<String, Long> expired = new LinkedHashMap<>();
        for (Line line : lines) {
            (line.dDay() < 0 ? expired : alive).putIfAbsent(line.name(), line.dDay());
        }
        List<String> sentences = new ArrayList<>();
        // 전체 보기는 임박 보기와 같은 이름이 앞에 와 같은 답처럼 보이지 않게, 가짓수를 먼저 말하고 곧 끝나는 것만 몇 개 쓴다
        if (scope == FridgeScope.ALL && !named && !alive.isEmpty()) {
            sentences.add("냉장고에 재료 " + alive.size() + "가지가 있어요.");
            List<String> soon = alive.entrySet().stream()
                    .filter(entry -> entry.getValue() <= LABEL_DAYS)
                    .map(entry -> entry.getKey() + label(entry.getValue()))
                    .toList();
            if (soon.isEmpty()) {
                sentences.add("소비기한이 곧 끝나는 재료는 없어요.");
            } else {
                String last = soon.size() > SOON_SHOWN ? "개" : lastSoonName(alive);
                sentences.add("곧 끝나는 건 " + joined(soon, SOON_SHOWN) + copula(last) + ".");
            }
        } else if (!alive.isEmpty()) {
            List<String> labeled = alive.entrySet().stream()
                    .map(entry -> entry.getKey() + label(entry.getValue()))
                    .toList();
            String names = joined(labeled, ALIVE_SHOWN);
            sentences.add("냉장고에 " + names + subjectParticle(alive.size() > ALIVE_SHOWN ? "개" : lastKey(alive)) + " 있어요.");
        }
        if (!expired.isEmpty()) {
            String names = joined(List.copyOf(expired.keySet()), EXPIRED_SHOWN);
            sentences.add(names + topicParticle(expired.size() > EXPIRED_SHOWN ? "개" : lastKey(expired)) + " 지났어요.");
        }
        if (!missing.isEmpty()) {
            sentences.add(String.join(", ", missing) + topicParticle(missing.get(missing.size() - 1)) + " 지금 냉장고에 없어요.");
        }
        if (sentences.isEmpty()) {
            return named ? "말씀하신 재료는 지금 냉장고에 없어요." : emptyReply(scope);
        }
        return String.join(" ", sentences);
    }

    static String emptyReply(FridgeScope scope) {
        return switch (scope) {
            case MEALKIT -> "냉장고에 밀키트가 없어요.";
            case IMMINENT -> "소비기한이 곧 끝나는 재료는 없어요.";
            case ALL -> "냉장고에 등록된 재료가 없어요.";
        };
    }

    // D-3 이내만 남은 날을 붙인다 (지난 줄은 따로 쓴다). 화면 임박 태그(formatImminentLabel)와 같은 말
    static String label(long dDay) {
        if (dDay < 0 || dDay > LABEL_DAYS) {
            return "";
        }
        if (dDay == 0) {
            return "(오늘까지)";
        }
        if (dDay == 1) {
            return "(내일까지)";
        }
        return dDay == 2 ? "(모레까지)" : "(" + dDay + "일 남음)";
    }

    private static String joined(List<String> names, int shown) {
        String head = String.join(", ", names.subList(0, Math.min(shown, names.size())));
        return names.size() > shown ? head + " 외 " + (names.size() - shown) + "개" : head;
    }

    private static String lastKey(Map<String, Long> names) {
        String last = null;
        for (String name : names.keySet()) {
            last = name;
        }
        return last;
    }

    // "곧 끝나는 건"에 쓴 마지막 이름 (D-3 이내 중 마지막)
    private static String lastSoonName(Map<String, Long> alive) {
        String last = null;
        for (Map.Entry<String, Long> entry : alive.entrySet()) {
            if (entry.getValue() <= LABEL_DAYS) {
                last = entry.getKey();
            }
        }
        return last;
    }

    // "○○예요" / "○○이에요" (받침으로 고른다)
    private static String copula(String word) {
        return hasFinalConsonant(word) ? "이에요" : "예요";
    }

    private static String subjectParticle(String word) {
        return hasFinalConsonant(word) ? "이" : "가";
    }

    private static String topicParticle(String word) {
        return hasFinalConsonant(word) ? "은" : "는";
    }

    private static boolean hasFinalConsonant(String word) {
        if (word == null || word.isEmpty()) {
            return false;
        }
        char last = word.charAt(word.length() - 1);
        return last >= '가' && last <= '힣' && (last - '가') % 28 != 0;
    }
}
