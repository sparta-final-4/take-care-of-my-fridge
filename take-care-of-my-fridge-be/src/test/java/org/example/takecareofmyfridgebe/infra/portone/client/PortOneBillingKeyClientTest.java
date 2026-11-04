package org.example.takecareofmyfridgebe.infra.portone.client;

import org.example.takecareofmyfridgebe.domain.payment.entity.CardType;
import org.example.takecareofmyfridgebe.domain.payment.entity.PgProvider;
import org.example.takecareofmyfridgebe.domain.payment.port.BillingKeyInfo;
import org.example.takecareofmyfridgebe.domain.payment.port.BillingKeyPayResult;
import org.example.takecareofmyfridgebe.infra.portone.config.PortOneProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// PortOne 빌링키 조회·삭제 응답을 흉내 내 변환과 오류 처리를 확인한다
class PortOneBillingKeyClientTest {

    private static final String BASE_URL = "https://api.portone.test";
    private static final String BILLING_KEY = "billing-key-1";
    private static final String GET_URL = BASE_URL + "/billing-keys/" + BILLING_KEY + "?storeId=store-1";
    private static final Map<PgProvider, String> CHANNELS = Arrays.stream(PgProvider.values())
            .collect(Collectors.toMap(Function.identity(), pgProvider -> "channel-" + pgProvider));

    private MockRestServiceServer server;
    private PortOneBillingKeyClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new PortOneBillingKeyClient(builder.build(),
                new PortOneProperties(BASE_URL, "secret", "store-1", CHANNELS, "channel-billing",
                        "whsec_dGVzdA=="));
    }

    @Test
    @DisplayName("정기결제 채널 키는 설정 값을 그대로 돌려준다")
    void billingChannelKey_returnConfiguredKey() {
        assertThat(client.billingChannelKey()).isEqualTo("channel-billing");
    }

    @Test
    @DisplayName("발급된 카드 빌링키를 조회하면 상태·고객 ID·카드 상품명·마스킹 번호·카드 종류·발급사 코드로 바꾼다")
    void getBillingKey_mapIssuedCard() {
        server.expect(requestTo(GET_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"status":"ISSUED","billingKey":"billing-key-1","storeId":"store-1",
                         "methods":[{"type":"BillingKeyPaymentMethodCard",
                           "card":{"name":"신한카드 Deep Dream","number":"53275012****1234","type":"CREDIT",
                                   "issuer":"SHINHAN_CARD","brand":"MASTER"}}],
                         "customer":{"id":"member-7"},"issuedAt":"2026-11-04T01:00:00Z"}
                        """, MediaType.APPLICATION_JSON));

        Optional<BillingKeyInfo> result = client.getBillingKey(BILLING_KEY);

        assertThat(result).contains(new BillingKeyInfo(
                "billing-key-1", true, "member-7", "신한카드 Deep Dream", "53275012****1234", CardType.CREDIT,
                "SHINHAN_CARD"));
        assertThat(result.orElseThrow().cardLast4()).isEqualTo("1234");
        server.verify();
    }

    @Test
    @DisplayName("삭제된 빌링키나 카드 정보가 없는 빌링키는 발급 완료가 아니거나 카드 값이 비어 있다")
    void getBillingKey_mapDeletedWithoutCard() {
        server.expect(requestTo(GET_URL))
                .andRespond(withSuccess("""
                        {"status":"DELETED","billingKey":"billing-key-1","customer":{"id":"member-7"},
                         "deletedAt":"2026-11-04T02:00:00Z"}
                        """, MediaType.APPLICATION_JSON));

        BillingKeyInfo result = client.getBillingKey(BILLING_KEY).orElseThrow();

        assertThat(result.issued()).isFalse();
        assertThat(result.cardName()).isNull();
        assertThat(result.cardType()).isNull();
        assertThat(result.cardLast4()).isNull();
    }

    @Test
    @DisplayName("응답 본문에 빌링키가 빠져도 조회한 빌링키 값으로 돌려준다")
    void getBillingKey_useRequestedBillingKey_whenResponseOmitsIt() {
        server.expect(requestTo(GET_URL))
                .andRespond(withSuccess("{\"status\":\"ISSUED\",\"customer\":{\"id\":\"member-7\"}}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.getBillingKey(BILLING_KEY).orElseThrow().billingKey()).isEqualTo(BILLING_KEY);
    }

    @Test
    @DisplayName("PortOne에 없는 빌링키를 조회하면 빈 값을 돌려준다")
    void getBillingKey_returnEmpty_whenNotFound() {
        server.expect(requestTo(GET_URL))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"type\":\"BILLING_KEY_NOT_FOUND\",\"message\":\"없음\"}"));

        assertThat(client.getBillingKey(BILLING_KEY)).isEmpty();
    }

    @Test
    @DisplayName("PortOne 서버 오류로 조회하지 못하면 예외를 던진다")
    void getBillingKey_throwException_whenServerError() {
        server.expect(requestTo(GET_URL)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> client.getBillingKey(BILLING_KEY)).isInstanceOf(HttpServerErrorException.class);
    }

    @Test
    @DisplayName("빌링키를 삭제하면 상점 ID·사유·요청 주체를 쿼리로 보낸다")
    void deleteBillingKey_success() {
        server.expect(requestTo(startsWith(BASE_URL + "/billing-keys/" + BILLING_KEY)))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(queryParam("storeId", "store-1"))
                .andExpect(queryParam("requester", "CUSTOMER"))
                .andRespond(withSuccess("{\"deletedAt\":\"2026-11-04T02:00:00Z\"}", MediaType.APPLICATION_JSON));

        client.deleteBillingKey(BILLING_KEY, "사용자 삭제");

        server.verify();
    }

    @Test
    @DisplayName("이미 삭제된 빌링키를 삭제하면 성공으로 본다")
    void deleteBillingKey_success_whenAlreadyDeleted() {
        server.expect(requestTo(startsWith(BASE_URL + "/billing-keys/" + BILLING_KEY)))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"type\":\"BILLING_KEY_ALREADY_DELETED\",\"message\":\"이미 삭제됨\"}"));

        client.deleteBillingKey(BILLING_KEY, "사용자 삭제");

        server.verify();
    }

    @Test
    @DisplayName("PortOne에 없는 빌링키를 삭제하면 이미 없는 것으로 보고 성공으로 본다")
    void deleteBillingKey_success_whenNotFound() {
        server.expect(requestTo(startsWith(BASE_URL + "/billing-keys/" + BILLING_KEY)))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"type\":\"BILLING_KEY_NOT_FOUND\",\"message\":\"없음\"}"));

        client.deleteBillingKey(BILLING_KEY, "사용자 삭제");

        server.verify();
    }

    @Test
    @DisplayName("그 밖의 이유로 삭제가 거절되면 예외를 던진다")
    void deleteBillingKey_throwException_whenRejected() {
        server.expect(requestTo(startsWith(BASE_URL + "/billing-keys/" + BILLING_KEY)))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"type\":\"FORBIDDEN\",\"message\":\"권한 없음\"}"));

        assertThatThrownBy(() -> client.deleteBillingKey(BILLING_KEY, "사용자 삭제"))
                .isInstanceOf(HttpClientErrorException.class);
    }

    private static final String PAY_URL = BASE_URL + "/payments/payment-1/billing-key";

    private BillingKeyPayResult pay() {
        return client.pay("payment-1", BILLING_KEY, "대파 외 1건", 10000L, "member-7");
    }

    @Test
    @DisplayName("빌링키 결제가 성공하면 결제됨을 돌려주고, 상점 ID·빌링키·주문명·금액·통화·고객 ID를 보낸다")
    void pay_returnPaid() {
        server.expect(requestTo(PAY_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.storeId").value("store-1"))
                .andExpect(jsonPath("$.billingKey").value(BILLING_KEY))
                .andExpect(jsonPath("$.orderName").value("대파 외 1건"))
                .andExpect(jsonPath("$.amount.total").value(10000))
                .andExpect(jsonPath("$.currency").value("KRW"))
                .andExpect(jsonPath("$.customer.id").value("member-7"))
                .andRespond(withSuccess("{\"payment\":{\"pgTxId\":\"tx-1\",\"paidAt\":\"2026-11-04T01:00:00Z\"}}",
                        MediaType.APPLICATION_JSON));

        assertThat(pay()).isEqualTo(BillingKeyPayResult.PAID);
        server.verify();
    }

    @Test
    @DisplayName("같은 결제 ID로 이미 결제됐으면 결제됨으로 본다")
    void pay_returnPaid_whenAlreadyPaid() {
        server.expect(requestTo(PAY_URL)).andRespond(withStatus(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_JSON).body("{\"type\":\"ALREADY_PAID\",\"message\":\"이미 결제됨\"}"));

        assertThat(pay()).isEqualTo(BillingKeyPayResult.PAID);
    }

    @Test
    @DisplayName("PG가 카드를 거절(502 PG_PROVIDER)하거나 빌링키가 없으면 거절로 본다")
    void pay_returnRejected_whenPgRejectsOrBillingKeyMissing() {
        server.expect(requestTo(PAY_URL)).andRespond(withStatus(HttpStatus.BAD_GATEWAY)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"type\":\"PG_PROVIDER\",\"pgCode\":\"REJECT\",\"pgMessage\":\"한도 초과\"}"));
        assertThat(pay()).isEqualTo(BillingKeyPayResult.REJECTED);

        server.reset();
        server.expect(requestTo(PAY_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON).body("{\"type\":\"BILLING_KEY_NOT_FOUND\"}"));
        assertThat(pay()).isEqualTo(BillingKeyPayResult.REJECTED);
    }

    @Test
    @DisplayName("요청 시간 초과(408)나 요청 과다(429)면 결과를 모르는 것으로 본다")
    void pay_returnUnknown_whenRequestTimeoutOrTooMany() {
        server.expect(requestTo(PAY_URL)).andRespond(withStatus(HttpStatus.REQUEST_TIMEOUT));
        assertThat(pay()).isEqualTo(BillingKeyPayResult.UNKNOWN);

        server.reset();
        server.expect(requestTo(PAY_URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        assertThat(pay()).isEqualTo(BillingKeyPayResult.UNKNOWN);
    }

    @Test
    @DisplayName("PortOne 서버 오류나 타임아웃이면 결과를 모르는 것으로 본다")
    void pay_returnUnknown_whenServerErrorOrTimeout() {
        server.expect(requestTo(PAY_URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertThat(pay()).isEqualTo(BillingKeyPayResult.UNKNOWN);

        server.reset();
        server.expect(requestTo(PAY_URL)).andRespond(request -> {
            throw new SocketTimeoutException("read timed out");
        });
        assertThat(pay()).isEqualTo(BillingKeyPayResult.UNKNOWN);
    }
}
