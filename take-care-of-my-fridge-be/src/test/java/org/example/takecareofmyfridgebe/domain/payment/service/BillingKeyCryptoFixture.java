package org.example.takecareofmyfridgebe.domain.payment.service;

import org.example.takecareofmyfridgebe.domain.payment.entity.EncryptedBillingKey;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.Arrays;
import java.util.Map;

// 테스트용 빌링키 암호화. 고정 키를 써서 테스트끼리 같은 암호문 규칙을 쓴다
public final class BillingKeyCryptoFixture {

    public static final String KEY_ID = "k1";

    private BillingKeyCryptoFixture() {
    }

    public static BillingKeyCrypto crypto() {
        return new BillingKeyCrypto(KEY_ID, Map.of(KEY_ID, filled((byte) 0x11)), filled((byte) 0x22));
    }

    public static EncryptedBillingKey encrypted(Long memberId, String billingKey) {
        return crypto().encrypt(memberId, billingKey);
    }

    static byte[] filled(byte value) {
        byte[] key = new byte[BillingKeyCrypto.KEY_BYTES];
        Arrays.fill(key, value);
        return key;
    }

    // @DataJpaTest 등 BillingKeyCryptoConfig를 읽지 않는 테스트 컨텍스트에 넣는다
    @TestConfiguration
    public static class Config {

        @Bean
        BillingKeyCrypto billingKeyCrypto() {
            return crypto();
        }
    }
}
