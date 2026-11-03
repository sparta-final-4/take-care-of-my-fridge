package org.example.takecareofmyfridgebe.domain.chat.service;

import com.openai.errors.InternalServerException;
import com.openai.errors.OpenAIException;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;
import com.openai.errors.RateLimitException;
import com.openai.errors.UnauthorizedException;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.io.IOException;

// OpenAI 호출 오류를 상담 에러 코드로 바꾼다.
// Spring AI는 스트림 오류를 IllegalStateException·CompletionException 등으로 감싸서 주므로 cause 사슬을 따라가며 찾는다
@Slf4j
@Component
public class ChatReplyErrorTranslator {

    private static final String INSUFFICIENT_QUOTA = "insufficient_quota";
    private static final int MAX_CAUSE_DEPTH = 10;

    /**
     * 답 생성 중 난 예외를 사용자에게 보낼 BusinessException으로 바꾼다.
     * OpenAI와 관련 없는 예외는 그대로 돌려준다(공통 서버 에러로 처리된다).
     *
     * @param error            답 생성 스트림에서 난 예외
     * @param afterFirstChunk  답 조각을 하나라도 보낸 뒤에 났는지
     * @return 바꾼 예외, 바꿀 대상이 아니면 받은 예외 그대로
     */
    public Throwable translate(Throwable error, boolean afterFirstChunk) {
        if (error instanceof BusinessException) {
            return error;
        }
        Throwable cause = findAiCause(error);
        if (cause == null) {
            return error;
        }

        if (afterFirstChunk) {
            log.warn("상담 답을 받다가 끊김 - {}", describe(cause));
            return new BusinessException(ErrorCode.CHAT_REPLY_INTERRUPTED);
        }

        if (cause instanceof UnauthorizedException) {
            log.error("OpenAI 키 오류(401) - {}", describe(cause));
            return new BusinessException(ErrorCode.CHAT_AI_UNAVAILABLE);
        }
        if (cause instanceof RateLimitException rateLimit) {
            if (rateLimit.code().filter(INSUFFICIENT_QUOTA::equals).isPresent()) {
                log.error("OpenAI 잔액 부족(insufficient_quota) - {}", describe(cause));
                return new BusinessException(ErrorCode.CHAT_AI_UNAVAILABLE);
            }
            log.warn("OpenAI 요청 한도 초과(429) - {}", describe(cause));
            return new BusinessException(ErrorCode.CHAT_AI_BUSY);
        }
        if (cause instanceof InternalServerException
                || cause instanceof OpenAIIoException
                || cause instanceof IOException) {
            log.warn("OpenAI 서버 오류·타임아웃·연결 실패 - {}", describe(cause));
            return new BusinessException(ErrorCode.CHAT_AI_UNAVAILABLE);
        }
        return error;
    }

    // OpenAI 예외를 먼저 찾고, 없으면 감싸지지 않은 입출력 예외(타임아웃·연결 실패)를 찾는다
    private Throwable findAiCause(Throwable error) {
        Throwable ioCause = null;
        Throwable current = error;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (current instanceof OpenAIException) {
                return current;
            }
            if (ioCause == null && current instanceof IOException) {
                ioCause = current;
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return ioCause;
    }

    private String describe(Throwable cause) {
        if (cause instanceof OpenAIServiceException service) {
            return "status: " + service.statusCode() + ", code: " + service.code().orElse("-")
                    + ", message: " + cause.getMessage();
        }
        return cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }
}
