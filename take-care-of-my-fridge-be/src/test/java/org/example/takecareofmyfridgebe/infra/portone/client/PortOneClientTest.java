package org.example.takecareofmyfridgebe.infra.portone.client;

import org.example.takecareofmyfridgebe.domain.payment.port.PaymentGatewayResponse;
import org.example.takecareofmyfridgebe.infra.portone.config.PortOneProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// PortOne API 응답을 흉내 내 조회 결과 변환과 취소 오류 처리를 확인한다
class PortOneClientTest {

    private static final String BASE_URL = "https://api.portone.test";
    private static final String PAYMENT_ID = "payment-1";

    private MockRestServiceServer server;
    private PortOneClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new PortOneClient(builder.build(),
                new PortOneProperties(BASE_URL, "secret", "store-1", "channel-1", "whsec_dGVzdA=="));
    }

    @Test
    @DisplayName("응답에 취소 금액이 없어도 조회에 성공하고 0으로 본다")
    void getPayment_missingCancelledAmount() {
        server.expect(requestTo(BASE_URL + "/payments/" + PAYMENT_ID + "?storeId=store-1"))
                .andRespond(withSuccess("""
                        {"id":"payment-1","status":"PAID","amount":{"total":10000,"paid":10000}}""", MediaType.APPLICATION_JSON));

        Optional<PaymentGatewayResponse> response = client.getPayment(PAYMENT_ID);

        assertThat(response).hasValueSatisfying(pg -> {
            assertThat(pg.isPaid()).isTrue();
            assertThat(pg.totalAmount()).isEqualTo(10000L);
            assertThat(pg.cancelledAmount()).isZero();
        });
    }

    @Test
    @DisplayName("결제 완료 시각(RFC 3339)을 서버 시간대의 시각으로 바꿔 돌려준다")
    void getPayment_convertsPaidAtToServerZone() {
        server.expect(requestTo(BASE_URL + "/payments/" + PAYMENT_ID + "?storeId=store-1"))
                .andRespond(withSuccess("""
                        {"id":"payment-1","status":"PAID","amount":{"total":10000,"cancelled":0},
                         "paidAt":"2026-10-20T01:09:59Z"}""", MediaType.APPLICATION_JSON));

        Optional<PaymentGatewayResponse> response = client.getPayment(PAYMENT_ID);

        LocalDateTime expected = LocalDateTime.ofInstant(Instant.parse("2026-10-20T01:09:59Z"), ZoneId.systemDefault());
        assertThat(response).hasValueSatisfying(pg -> assertThat(pg.paidAt()).isEqualTo(expected));
    }

    @Test
    @DisplayName("결제가 끝나지 않아 결제 완료 시각이 없으면 null로 둔다")
    void getPayment_withoutPaidAt() {
        server.expect(requestTo(BASE_URL + "/payments/" + PAYMENT_ID + "?storeId=store-1"))
                .andRespond(withSuccess("""
                        {"id":"payment-1","status":"READY","amount":{"total":10000,"cancelled":0}}""", MediaType.APPLICATION_JSON));

        assertThat(client.getPayment(PAYMENT_ID)).hasValueSatisfying(pg -> assertThat(pg.paidAt()).isNull());
    }

    @Test
    @DisplayName("PG에 결제가 없으면(404) 빈 값을 돌려준다")
    void getPayment_notFound() {
        server.expect(requestTo(BASE_URL + "/payments/" + PAYMENT_ID + "?storeId=store-1"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(client.getPayment(PAYMENT_ID)).isEmpty();
    }

    @Test
    @DisplayName("부분 취소에 성공하면 요청 금액을 보내고 PG 취소 건 ID를 돌려준다")
    void cancelPayment_returnsCancellationId() {
        server.expect(requestTo(BASE_URL + "/payments/" + PAYMENT_ID + "/cancel"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.amount").value(6000))
                .andRespond(withSuccess("""
                        {"cancellation":{"status":"SUCCEEDED","id":"cancellation-1","totalAmount":6000}}""",
                        MediaType.APPLICATION_JSON));

        assertThat(client.cancelPayment(PAYMENT_ID, "단순 변심", 6000L)).isEqualTo("cancellation-1");
        server.verify();
    }

    @Test
    @DisplayName("이미 취소된 결제를 전액 취소하면 성공으로 보고 취소 건 ID는 없다")
    void cancelPayment_alreadyCancelled_succeeds() {
        server.expect(requestTo(BASE_URL + "/payments/" + PAYMENT_ID + "/cancel"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"type\":\"PAYMENT_ALREADY_CANCELLED\",\"message\":\"이미 취소된 결제입니다.\"}"));

        assertThat(client.cancelPayment(PAYMENT_ID, "보상 취소", null)).isNull();
        server.verify();
    }

    @Test
    @DisplayName("이미 취소된 결제를 부분 취소하면 이번 금액이 환불되지 않았으므로 그대로 던진다")
    void cancelPayment_partialAlreadyCancelled_throws() {
        server.expect(requestTo(BASE_URL + "/payments/" + PAYMENT_ID + "/cancel"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"type\":\"PAYMENT_ALREADY_CANCELLED\",\"message\":\"이미 취소된 결제입니다.\"}"));

        assertThatThrownBy(() -> client.cancelPayment(PAYMENT_ID, "단순 변심", 6000L))
                .isInstanceOf(HttpClientErrorException.class);
        server.verify();
    }

    @Test
    @DisplayName("이미 취소된 경우가 아닌 취소 오류는 그대로 던진다")
    void cancelPayment_otherError_throws() {
        server.expect(requestTo(BASE_URL + "/payments/" + PAYMENT_ID + "/cancel"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"type\":\"CANCELLABLE_AMOUNT_CONSISTENCY_BROKEN\",\"message\":\"취소 가능 금액 불일치\"}"));

        assertThatThrownBy(() -> client.cancelPayment(PAYMENT_ID, "보상 취소", null))
                .isInstanceOf(HttpClientErrorException.class);
    }

    @Test
    @DisplayName("오류 본문을 해석할 수 없으면 이미 취소된 것으로 보지 않고 던진다")
    void cancelPayment_unreadableError_throws() {
        server.expect(requestTo(BASE_URL + "/payments/" + PAYMENT_ID + "/cancel"))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.TEXT_PLAIN).body("conflict"));

        assertThatThrownBy(() -> client.cancelPayment(PAYMENT_ID, "보상 취소", null))
                .isInstanceOf(HttpClientErrorException.class);
    }
}
