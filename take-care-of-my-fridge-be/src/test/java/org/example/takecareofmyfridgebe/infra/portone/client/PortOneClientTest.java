package org.example.takecareofmyfridgebe.infra.portone.client;

import org.example.takecareofmyfridgebe.domain.payment.entity.PgProvider;
import org.example.takecareofmyfridgebe.domain.payment.port.PaymentCheckout;
import org.example.takecareofmyfridgebe.domain.payment.port.PaymentGatewayResponse;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.example.takecareofmyfridgebe.infra.portone.config.PortOneProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// PortOne API 응답을 흉내 내 조회 결과 변환과 취소 오류 처리를 확인한다
class PortOneClientTest {

    private static final String BASE_URL = "https://api.portone.test";
    private static final String PAYMENT_ID = "payment-1";
    private static final Map<PgProvider, String> CHANNELS = Map.of(
            PgProvider.KAKAOPAY, "channel-kakaopay",
            PgProvider.KICC, "channel-kicc",
            PgProvider.INICIS, "channel-inicis",
            PgProvider.TOSSPAYMENTS, "channel-tosspayments",
            PgProvider.TOSSPAY, "channel-tosspay");

    private MockRestServiceServer server;
    private PortOneClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new PortOneClient(builder.build(),
                new PortOneProperties(BASE_URL, "secret", "store-1", CHANNELS, "whsec_dGVzdA=="));
    }

    @Test
    @DisplayName("카카오페이·토스페이는 간편결제 결제창, 나머지 결제대행사는 카드 결제창 값을 돌려준다")
    void getCheckout_returnCheckoutByPgProvider() {
        assertThat(client.getCheckout(PgProvider.KAKAOPAY))
                .isEqualTo(new PaymentCheckout("channel-kakaopay", "EASY_PAY", "KAKAOPAY"));
        assertThat(client.getCheckout(PgProvider.KICC)).isEqualTo(new PaymentCheckout("channel-kicc", "CARD", null));
        assertThat(client.getCheckout(PgProvider.INICIS)).isEqualTo(new PaymentCheckout("channel-inicis", "CARD", null));
        assertThat(client.getCheckout(PgProvider.TOSSPAYMENTS))
                .isEqualTo(new PaymentCheckout("channel-tosspayments", "CARD", null));
        assertThat(client.getCheckout(PgProvider.TOSSPAY))
                .isEqualTo(new PaymentCheckout("channel-tosspay", "EASY_PAY", "TOSSPAY"));
    }

    @Test
    @DisplayName("결제대행사 하나라도 채널 키가 없으면 설정을 만들 수 없다")
    void portOneProperties_throwException_whenChannelKeyMissing() {
        Map<PgProvider, String> missingInicis = Map.of(
                PgProvider.KAKAOPAY, "channel-kakaopay",
                PgProvider.KICC, "channel-kicc",
                PgProvider.TOSSPAYMENTS, " ",
                PgProvider.TOSSPAY, "channel-tosspay");

        assertThatThrownBy(() -> new PortOneProperties(BASE_URL, "secret", "store-1", missingInicis, "whsec_dGVzdA=="))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("INICIS")
                .hasMessageContaining("TOSSPAYMENTS");
    }

    @Test
    @DisplayName("응답 채널의 PG 코드를 결제대행사로 바꾸고, 쓰지 않는 PG면 null로 둔다")
    void getPayment_mapPgProvider() {
        for (String pgProvider : new String[]{"INICIS_V2", "PG_PROVIDER_TOSSPAYMENTS", "TOSSPAY_V2", "NICE_V2"}) {
            server.expect(requestTo(BASE_URL + "/payments/" + PAYMENT_ID + "?storeId=store-1"))
                    .andRespond(withSuccess(paymentWithPg(pgProvider), MediaType.APPLICATION_JSON));
        }

        assertThat(client.getPayment(PAYMENT_ID))
                .hasValueSatisfying(pg -> assertThat(pg.pgProvider()).isEqualTo(PgProvider.INICIS));
        assertThat(client.getPayment(PAYMENT_ID))
                .hasValueSatisfying(pg -> assertThat(pg.pgProvider()).isEqualTo(PgProvider.TOSSPAYMENTS));
        assertThat(client.getPayment(PAYMENT_ID))
                .hasValueSatisfying(pg -> assertThat(pg.pgProvider()).isEqualTo(PgProvider.TOSSPAY));
        assertThat(client.getPayment(PAYMENT_ID)).hasValueSatisfying(pg -> assertThat(pg.pgProvider()).isNull());
    }

    private static String paymentWithPg(String pgProvider) {
        return """
                {"id":"payment-1","status":"PAID","amount":{"total":10000,"cancelled":0},
                 "channel":{"type":"TEST","key":"channel-1","pgProvider":"%s"}}""".formatted(pgProvider);
    }

    @Test
    @DisplayName("응답에 취소 금액이 없어도 조회에 성공하고 0으로 본다")
    void getPayment_treatCancelledAmountAsZero_whenCancelledAmountMissing() {
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
    void getPayment_convertPaidAtToServerZone() {
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
    void getPayment_returnNullPaidAt_whenPaidAtMissing() {
        server.expect(requestTo(BASE_URL + "/payments/" + PAYMENT_ID + "?storeId=store-1"))
                .andRespond(withSuccess("""
                        {"id":"payment-1","status":"READY","amount":{"total":10000,"cancelled":0}}""", MediaType.APPLICATION_JSON));

        assertThat(client.getPayment(PAYMENT_ID)).hasValueSatisfying(pg -> assertThat(pg.paidAt()).isNull());
    }

    @Test
    @DisplayName("PG에 결제가 없으면(404) 빈 값을 돌려준다")
    void getPayment_returnEmpty_whenPaymentNotFound() {
        server.expect(requestTo(BASE_URL + "/payments/" + PAYMENT_ID + "?storeId=store-1"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(client.getPayment(PAYMENT_ID)).isEmpty();
    }

    @Test
    @DisplayName("부분 취소에 성공하면 요청 금액을 보내고 PG 취소 건 ID를 돌려준다")
    void cancelPayment_returnCancellationId() {
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
    void cancelPayment_success_whenAlreadyCancelled() {
        server.expect(requestTo(BASE_URL + "/payments/" + PAYMENT_ID + "/cancel"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"type\":\"PAYMENT_ALREADY_CANCELLED\",\"message\":\"이미 취소된 결제입니다.\"}"));

        assertThat(client.cancelPayment(PAYMENT_ID, "보상 취소", null)).isNull();
        server.verify();
    }

    @Test
    @DisplayName("이미 취소된 결제를 부분 취소하면 이번 금액이 환불되지 않았으므로 거절로 던진다")
    void cancelPayment_throwException_whenPartialCancelOnAlreadyCancelled() {
        server.expect(requestTo(BASE_URL + "/payments/" + PAYMENT_ID + "/cancel"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"type\":\"PAYMENT_ALREADY_CANCELLED\",\"message\":\"이미 취소된 결제입니다.\"}"));

        assertThatThrownBy(() -> client.cancelPayment(PAYMENT_ID, "단순 변심", 6000L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PG_CANCEL_REJECTED);
        server.verify();
    }

    @Test
    @DisplayName("이미 취소된 경우가 아닌 4xx 취소 오류는 PG 거절로 던지고 PG 오류 내용을 메시지에 담는다")
    void cancelPayment_throwException_whenOtherClientError() {
        server.expect(requestTo(BASE_URL + "/payments/" + PAYMENT_ID + "/cancel"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"type\":\"CANCELLABLE_AMOUNT_CONSISTENCY_BROKEN\",\"message\":\"취소 가능 금액 불일치\"}"));

        assertThatThrownBy(() -> client.cancelPayment(PAYMENT_ID, "보상 취소", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("CANCELLABLE_AMOUNT_CONSISTENCY_BROKEN")
                .extracting("errorCode").isEqualTo(ErrorCode.PG_CANCEL_REJECTED);
    }

    @Test
    @DisplayName("오류 본문을 해석할 수 없으면 이미 취소된 것으로 보지 않고 던진다")
    void cancelPayment_throwException_whenErrorBodyUnreadable() {
        server.expect(requestTo(BASE_URL + "/payments/" + PAYMENT_ID + "/cancel"))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.TEXT_PLAIN).body("conflict"));

        assertThatThrownBy(() -> client.cancelPayment(PAYMENT_ID, "보상 취소", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("conflict")
                .extracting("errorCode").isEqualTo(ErrorCode.PG_CANCEL_REJECTED);
    }

    @Test
    @DisplayName("넘겨받은 멱등 키를 따옴표로 감싸 Idempotency-Key 헤더로 보낸다")
    void cancelPayment_sendIdempotencyKey() {
        server.expect(requestTo(BASE_URL + "/payments/" + PAYMENT_ID + "/cancel"))
                .andExpect(header("Idempotency-Key", "\"refund-100\""))
                .andRespond(withSuccess("""
                        {"cancellation":{"status":"SUCCEEDED","id":"cancellation-1","totalAmount":6000}}""",
                        MediaType.APPLICATION_JSON));

        assertThat(client.cancelPayment(PAYMENT_ID, "단순 변심", 6000L, "refund-100")).isEqualTo("cancellation-1");
        server.verify();
    }

    @Test
    @DisplayName("타임아웃은 같은 멱등 키로 3번까지 시도하고, 마지막도 실패하면 결과를 모르므로 그대로 던진다")
    void cancelPayment_retryThenThrowException_whenTimeout() {
        server.expect(ExpectedCount.times(3), requestTo(BASE_URL + "/payments/" + PAYMENT_ID + "/cancel"))
                .andExpect(header("Idempotency-Key", "\"refund-100\""))
                .andRespond(request -> {
                    throw new SocketTimeoutException("timeout");
                });

        assertThatThrownBy(() -> client.cancelPayment(PAYMENT_ID, "단순 변심", 6000L, "refund-100"))
                .isInstanceOf(ResourceAccessException.class);
        server.verify();
    }

    @Test
    @DisplayName("부분 취소된 결제를 조회하면 취소 건 ID·금액·완료 여부를 함께 돌려준다")
    void getPayment_returnCancellations_whenPartialCancelled() {
        server.expect(requestTo(BASE_URL + "/payments/" + PAYMENT_ID + "?storeId=store-1"))
                .andRespond(withSuccess("""
                        {"id":"payment-1","status":"PARTIAL_CANCELLED","amount":{"total":10000,"cancelled":6000},
                         "cancellations":[
                           {"status":"SUCCEEDED","id":"cancellation-1","totalAmount":6000},
                           {"status":"FAILED","id":"cancellation-2","totalAmount":1000}]}""",
                        MediaType.APPLICATION_JSON));

        assertThat(client.getPayment(PAYMENT_ID)).hasValueSatisfying(pg -> {
            assertThat(pg.cancelledAmount()).isEqualTo(6000L);
            assertThat(pg.cancellations()).containsExactly(
                    new PaymentGatewayResponse.Cancellation("cancellation-1", 6000L, true),
                    new PaymentGatewayResponse.Cancellation("cancellation-2", 1000L, false));
        });
    }
}
