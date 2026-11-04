package org.example.takecareofmyfridgebe.global.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/chat-sessions/7/messages");

    @Test
    @DisplayName("스트리밍 중 연결이 끊겨 쓰기에 실패한 IOException은 응답 없이 끊김으로만 처리한다")
    void returnsNoBody_whenClientDisconnected() {
        assertThat(handler.handleIoException(new IOException("Broken pipe"), request)).isNull();
        assertThat(handler.handleIoException(new IOException("Connection reset by peer"), request)).isNull();
    }

    @Test
    @DisplayName("연결 끊김이 아닌 IOException은 서버 오류로 응답한다")
    void returnsServerError_whenOtherIoException() {
        assertThat(handler.handleIoException(new IOException("디스크가 가득 찼습니다"), request).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("응답을 더 쓸 수 없는 비동기 요청 예외는 예외 없이 끊김으로만 처리한다")
    void handlesAsyncRequestNotUsable() {
        handler.handleAsyncRequestNotUsable(new AsyncRequestNotUsableException("Response not usable"), request);

        assertThat(GlobalExceptionHandler.isClientDisconnect(new IOException("Broken pipe"))).isTrue();
        assertThat(GlobalExceptionHandler.isClientDisconnect(new IOException((String) null))).isFalse();
    }
}
