package org.example.takecareofmyfridgebe.domain.chat.dto;

/**
 * 서버 단어로 걸러 AI를 부르지 않는 메시지 종류와 그때 보내는 안내 답 (SafetyWordChecker).
 * 의도 판단이 BLOCKED면 BANNED의 안내 답을, SELF_HARM이면 SELF_HARM의 안내 답을 같이 쓴다.
 * 걸린 말은 하루 횟수를 세지 않고, 안내 답과 함께 표시(filtered)해 저장한다.
 */
public enum ChatSafetyBlock {
    // 자해·자살을 말함. 금지어보다 먼저 본다 (안내를 다르게 한다)
    SELF_HARM("많이 힘드시죠. 혼자 견디지 마시고 자살예방상담전화 109(24시간, 무료)에 이야기해 주세요."),
    // 욕설·불법·위험한 말
    BANNED("그 이야기는 도와드릴 수 없어요. 요리나 식재료에 대해 물어봐 주세요."),
    // 지시를 무시하라거나 프롬프트를 공개하라는 말 ("너는 이제부터 ~야"처럼 역할만 정하는 말은 거르지 않는다)
    INJECTION("그 요청은 들어드릴 수 없어요. 요리나 냉장고 재료에 대해 물어봐 주세요.");

    private final String reply;

    ChatSafetyBlock(String reply) {
        this.reply = reply;
    }

    public String reply() {
        return reply;
    }
}
