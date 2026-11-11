package org.example.takecareofmyfridgebe.domain.chat.ai;

import com.openai.core.http.Headers;
import com.openai.errors.BadRequestException;
import com.openai.errors.InternalServerException;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.RateLimitException;
import com.openai.errors.UnauthorizedException;
import com.openai.models.ErrorObject;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.SocketTimeoutException;
import java.util.Optional;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;

class ChatReplyErrorTranslatorTest {

    private final ChatReplyErrorTranslator translator = new ChatReplyErrorTranslator();

    @Test
    @DisplayName("첫 조각 전에 OpenAI 키 오류(401)가 나면 CHAT_AI_UNAVAILABLE로 바꾼다")
    void returnsAiUnavailable_whenUnauthorized() {
        UnauthorizedException error = UnauthorizedException.builder()
                .headers(Headers.builder().build())
                .error(errorObject("invalid_api_key"))
                .build();

        assertThat(translator.translate(error, false))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CHAT_AI_UNAVAILABLE);
    }

    @Test
    @DisplayName("첫 조각 전에 잔액 부족(insufficient_quota) 429가 나면 CHAT_AI_UNAVAILABLE로 바꾼다")
    void returnsAiUnavailable_whenQuotaIsInsufficient() {
        RateLimitException error = RateLimitException.builder()
                .headers(Headers.builder().build())
                .error(errorObject("insufficient_quota"))
                .build();

        assertThat(translator.translate(error, false))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CHAT_AI_UNAVAILABLE);
    }

    @Test
    @DisplayName("첫 조각 전에 요청 한도 429가 나면 CHAT_AI_BUSY로 바꾼다")
    void returnsAiBusy_whenRateLimited() {
        RateLimitException error = RateLimitException.builder()
                .headers(Headers.builder().build())
                .error(errorObject("rate_limit_exceeded"))
                .build();

        assertThat(translator.translate(error, false))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CHAT_AI_BUSY);
    }

    @Test
    @DisplayName("첫 조각 전에 OpenAI 5xx가 나면 CHAT_AI_UNAVAILABLE로 바꾼다")
    void returnsAiUnavailable_whenServerErrorOccurs() {
        InternalServerException error = InternalServerException.builder()
                .statusCode(503)
                .headers(Headers.builder().build())
                .build();

        assertThat(translator.translate(error, false))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CHAT_AI_UNAVAILABLE);
    }

    @Test
    @DisplayName("첫 조각 전에 타임아웃·연결 실패(입출력 예외)가 나면 CHAT_AI_UNAVAILABLE로 바꾼다")
    void returnsAiUnavailable_whenIoFails() {
        assertThat(translator.translate(new OpenAIIoException("Request failed", new SocketTimeoutException()), false))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CHAT_AI_UNAVAILABLE);
        assertThat(translator.translate(new SocketTimeoutException("timeout"), false))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CHAT_AI_UNAVAILABLE);
    }

    @Test
    @DisplayName("IllegalStateException·CompletionException 안에 감싸진 OpenAI 예외도 찾아서 바꾼다")
    void findsWrappedOpenAiException() {
        RateLimitException rateLimit = RateLimitException.builder()
                .headers(Headers.builder().build())
                .error(errorObject("rate_limit_exceeded"))
                .build();
        Throwable wrapped = new IllegalStateException("Stream processing failed", new CompletionException(rateLimit));

        assertThat(translator.translate(wrapped, false))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CHAT_AI_BUSY);
    }

    @Test
    @DisplayName("첫 조각 뒤에 OpenAI 예외가 나면 종류와 관계없이 CHAT_REPLY_INTERRUPTED로 바꾼다")
    void returnsInterrupted_whenFailsAfterFirstChunk() {
        Throwable wrapped = new IllegalStateException("Stream processing failed",
                new OpenAIIoException("Stream failed", new SocketTimeoutException()));

        assertThat(translator.translate(wrapped, true))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CHAT_REPLY_INTERRUPTED);
    }

    @Test
    @DisplayName("OpenAI와 관계없는 예외와 다른 OpenAI 오류(400 등)는 바꾸지 않고 그대로 돌려준다")
    void returnsSameError_whenNotTranslatable() {
        RuntimeException unrelated = new RuntimeException("버그");
        BadRequestException badRequest = BadRequestException.builder()
                .headers(Headers.builder().build())
                .error(errorObject("invalid_request"))
                .build();

        assertThat(translator.translate(unrelated, false)).isSameAs(unrelated);
        assertThat(translator.translate(unrelated, true)).isSameAs(unrelated);
        assertThat(translator.translate(badRequest, false)).isSameAs(badRequest);
    }

    @Test
    @DisplayName("이미 BusinessException이면 그대로 돌려준다")
    void returnsSameError_whenBusinessException() {
        BusinessException error = new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND);

        assertThat(translator.translate(error, true)).isSameAs(error);
    }

    private ErrorObject errorObject(String code) {
        return ErrorObject.builder()
                .code(code)
                .message("테스트 오류")
                .param(Optional.empty())
                .type("test_error")
                .build();
    }
}
