package org.example.takecareofmyfridgebe.domain.payment.service;

import org.example.takecareofmyfridgebe.domain.payment.entity.EncryptedBillingKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BillingKeyCryptoTest {

    private static final String BILLING_KEY = "billing-key-0193a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a5b";
    private static final byte[] KEY_1 = BillingKeyCryptoFixture.filled((byte) 0x11);
    private static final byte[] KEY_2 = BillingKeyCryptoFixture.filled((byte) 0x33);
    private static final byte[] HMAC_KEY = BillingKeyCryptoFixture.filled((byte) 0x22);

    private final BillingKeyCrypto crypto = BillingKeyCryptoFixture.crypto();

    @Test
    @DisplayName("암호화한 빌링키를 같은 회원 ID로 풀면 원래 빌링키가 나오고, 암호문과 지문에는 원문이 없다")
    void decryptReturnsOriginal_whenSameMember() {
        EncryptedBillingKey encrypted = crypto.encrypt(1L, BILLING_KEY);

        assertThat(crypto.decrypt(1L, encrypted.ciphertext())).isEqualTo(BILLING_KEY);
        assertThat(encrypted.ciphertext()).startsWith("v1:k1:").doesNotContain(BILLING_KEY);
        assertThat(encrypted.hash()).hasSize(64).matches("[0-9a-f]{64}").doesNotContain(BILLING_KEY);
    }

    @Test
    @DisplayName("같은 빌링키를 두 번 암호화하면 암호문은 매번 다르고 지문은 같다")
    void encryptReturnsDifferentCiphertextAndSameHash_whenSameBillingKey() {
        EncryptedBillingKey first = crypto.encrypt(1L, BILLING_KEY);
        EncryptedBillingKey second = crypto.encrypt(1L, BILLING_KEY);

        assertThat(first.ciphertext()).isNotEqualTo(second.ciphertext());
        assertThat(first.hash()).isEqualTo(second.hash()).isEqualTo(crypto.hash(BILLING_KEY));
        assertThat(crypto.hash("billing-key-other")).isNotEqualTo(first.hash());
    }

    @Test
    @DisplayName("다른 회원 ID로 풀면 실패한다(다른 회원 행에 옮겨 붙인 암호문)")
    void throwsException_whenDecryptedWithOtherMember() {
        EncryptedBillingKey encrypted = crypto.encrypt(1L, BILLING_KEY);

        assertThatThrownBy(() -> crypto.decrypt(2L, encrypted.ciphertext()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining(BILLING_KEY);
    }

    @Test
    @DisplayName("암호문을 한 바이트라도 바꾸면 복호화가 실패한다")
    void throwsException_whenCiphertextTampered() {
        String ciphertext = crypto.encrypt(1L, BILLING_KEY).ciphertext();
        String[] parts = ciphertext.split(":", 3);
        byte[] payload = Base64.getDecoder().decode(parts[2]);
        payload[payload.length - 1] ^= 0x01;
        String tampered = parts[0] + ":" + parts[1] + ":" + Base64.getEncoder().encodeToString(payload);

        assertThatThrownBy(() -> crypto.decrypt(1L, tampered)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("형식이 틀리거나 모르는 키 ID의 암호문은 풀지 않는다")
    void throwsException_whenFormatInvalidOrKeyUnknown() {
        assertThatThrownBy(() -> crypto.decrypt(1L, BILLING_KEY)).isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining(BILLING_KEY);
        assertThatThrownBy(() -> crypto.decrypt(1L, "v2:k1:AAAA")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> crypto.decrypt(1L, "v1:k9:AAAAAAAAAAAAAAAAAAAAAAAA"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> crypto.decrypt(1L, "v1:k1:!!!")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> crypto.decrypt(1L, "v1:k1:AAAA")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("암호화 키를 바꾼 뒤에도 예전 키로 만든 암호문을 풀고, 새 암호문은 새 키로 만든다")
    void decryptsOldCiphertext_whenKeyRotated() {
        BillingKeyCrypto before = new BillingKeyCrypto("k1", Map.of("k1", KEY_1), HMAC_KEY);
        String oldCiphertext = before.encrypt(1L, BILLING_KEY).ciphertext();

        BillingKeyCrypto after = new BillingKeyCrypto("k2", Map.of("k1", KEY_1, "k2", KEY_2), HMAC_KEY);
        EncryptedBillingKey renewed = after.encrypt(1L, BILLING_KEY);

        assertThat(after.decrypt(1L, oldCiphertext)).isEqualTo(BILLING_KEY);
        assertThat(renewed.ciphertext()).startsWith("v1:k2:");
        assertThat(renewed.hash()).isEqualTo(before.hash(BILLING_KEY));
    }

    @Test
    @DisplayName("빌링키가 비었거나 200바이트를 넘으면 암호화하지 않는다")
    void throwsException_whenBillingKeyBlankOrTooLong() {
        assertThatThrownBy(() -> crypto.encrypt(1L, " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> crypto.encrypt(null, BILLING_KEY)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> crypto.encrypt(1L, "a".repeat(201))).isInstanceOf(IllegalArgumentException.class);

        String longest = "a".repeat(200);
        EncryptedBillingKey encrypted = crypto.encrypt(1L, longest);
        assertThat(encrypted.ciphertext().length()).isLessThanOrEqualTo(512);
        assertThat(crypto.decrypt(1L, encrypted.ciphertext())).isEqualTo(longest);
    }

    @Test
    @DisplayName("키 길이가 틀리거나 지금 쓰는 키가 없거나 키 ID 형식이 틀리면 만들지 않는다")
    void throwsException_whenKeysInvalid() {
        assertThatThrownBy(() -> new BillingKeyCrypto("k1", Map.of("k1", new byte[16]), HMAC_KEY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BillingKeyCrypto("k2", Map.of("k1", KEY_1), HMAC_KEY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BillingKeyCrypto("k1", Map.of("k1", KEY_1), new byte[16]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BillingKeyCrypto("k:1", Map.of("k:1", KEY_1), HMAC_KEY))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
