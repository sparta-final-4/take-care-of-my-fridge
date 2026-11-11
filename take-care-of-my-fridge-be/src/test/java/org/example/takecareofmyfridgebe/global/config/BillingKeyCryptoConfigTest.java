package org.example.takecareofmyfridgebe.global.config;

import org.example.takecareofmyfridgebe.domain.payment.service.BillingKeyCrypto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BillingKeyCryptoConfigTest {

    private static final String BILLING_KEY = "billing-key-1";

    private final BillingKeyCryptoConfig config = new BillingKeyCryptoConfig();

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("배포 서버(prod)에 키 설정이 없으면 기동하지 않는다")
    void throwsException_whenDeployedWithoutKeys() {
        MockEnvironment environment = localEnvironment().withProperty(BillingKeyCryptoConfig.HMAC_KEY, key(0x22));
        environment.setActiveProfiles("prod");

        assertThatThrownBy(() -> config.billingKeyCrypto(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(BillingKeyCryptoConfig.ENCRYPTION_KEY);
        assertThat(tempDir).isEmptyDirectory();
    }

    @Test
    @DisplayName("배포 서버(prod)는 설정한 키로 암호화하고, 지금 키 ID를 암호문에 붙인다")
    void usesConfiguredKeys_whenDeployed() {
        MockEnvironment environment = localEnvironment()
                .withProperty(BillingKeyCryptoConfig.ENCRYPTION_KEY, key(0x11))
                .withProperty(BillingKeyCryptoConfig.HMAC_KEY, key(0x22))
                .withProperty(BillingKeyCryptoConfig.ENCRYPTION_KEY_ID, "k2");
        environment.setActiveProfiles("prod");

        BillingKeyCrypto crypto = config.billingKeyCrypto(environment);

        String ciphertext = crypto.encrypt(1L, BILLING_KEY).ciphertext();
        assertThat(ciphertext).startsWith("v1:k2:");
        assertThat(crypto.decrypt(1L, ciphertext)).isEqualTo(BILLING_KEY);
        assertThat(tempDir).isEmptyDirectory();
    }

    @Test
    @DisplayName("로컬에서 키 설정이 없으면 키 파일을 한 번 만들고, 다시 띄워도 같은 키를 쓴다")
    void createsAndReusesLocalKeyFiles_whenLocalWithoutKeys() throws Exception {
        BillingKeyCrypto first = config.billingKeyCrypto(localEnvironment());
        String ciphertext = first.encrypt(1L, BILLING_KEY).ciphertext();

        BillingKeyCrypto restarted = config.billingKeyCrypto(localEnvironment());

        assertThat(restarted.decrypt(1L, ciphertext)).isEqualTo(BILLING_KEY);
        assertThat(restarted.hash(BILLING_KEY)).isEqualTo(first.hash(BILLING_KEY));
        assertThat(Base64.getDecoder().decode(Files.readString(tempDir.resolve("encryption.key")))).hasSize(32);
        assertThat(Base64.getDecoder().decode(Files.readString(tempDir.resolve("hmac.key")))).hasSize(32);
        assertThat(Files.readString(tempDir.resolve("encryption.key")))
                .isNotEqualTo(Files.readString(tempDir.resolve("hmac.key")));
    }

    @Test
    @DisplayName("예전 키 목록을 넣으면 키를 바꾼 뒤에도 예전 암호문을 푼다")
    void decryptsWithRetiredKeys_whenConfigured() {
        MockEnvironment before = localEnvironment()
                .withProperty(BillingKeyCryptoConfig.ENCRYPTION_KEY, key(0x11))
                .withProperty(BillingKeyCryptoConfig.HMAC_KEY, key(0x22));
        String oldCiphertext = config.billingKeyCrypto(before).encrypt(1L, BILLING_KEY).ciphertext();

        MockEnvironment after = localEnvironment()
                .withProperty(BillingKeyCryptoConfig.ENCRYPTION_KEY, key(0x33))
                .withProperty(BillingKeyCryptoConfig.ENCRYPTION_KEY_ID, "k2")
                .withProperty(BillingKeyCryptoConfig.RETIRED_ENCRYPTION_KEYS, "k1=" + key(0x11))
                .withProperty(BillingKeyCryptoConfig.HMAC_KEY, key(0x22));

        assertThat(config.billingKeyCrypto(after).decrypt(1L, oldCiphertext)).isEqualTo(BILLING_KEY);
    }

    @Test
    @DisplayName("예전 키 목록에 지금 키 ID가 있으면 예전 키가 덮이지 않게 기동하지 않는다")
    void throwsException_whenRetiredKeyIdEqualsCurrentKeyId() {
        MockEnvironment sameId = localEnvironment()
                .withProperty(BillingKeyCryptoConfig.ENCRYPTION_KEY, key(0x33))
                .withProperty(BillingKeyCryptoConfig.RETIRED_ENCRYPTION_KEYS, "k1=" + key(0x11))
                .withProperty(BillingKeyCryptoConfig.HMAC_KEY, key(0x22));

        assertThatThrownBy(() -> config.billingKeyCrypto(sameId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(BillingKeyCryptoConfig.ENCRYPTION_KEY_ID);
    }

    @Test
    @DisplayName("키가 Base64가 아니거나 예전 키 목록 형식이 틀리면 기동하지 않는다")
    void throwsException_whenKeyFormatInvalid() {
        MockEnvironment notBase64 = localEnvironment()
                .withProperty(BillingKeyCryptoConfig.ENCRYPTION_KEY, "not base64!")
                .withProperty(BillingKeyCryptoConfig.HMAC_KEY, key(0x22));
        MockEnvironment badRetired = localEnvironment()
                .withProperty(BillingKeyCryptoConfig.ENCRYPTION_KEY, key(0x11))
                .withProperty(BillingKeyCryptoConfig.HMAC_KEY, key(0x22))
                .withProperty(BillingKeyCryptoConfig.RETIRED_ENCRYPTION_KEYS, "k0");

        assertThatThrownBy(() -> config.billingKeyCrypto(notBase64)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> config.billingKeyCrypto(badRetired)).isInstanceOf(IllegalStateException.class);
    }

    private MockEnvironment localEnvironment() {
        return new MockEnvironment().withProperty(BillingKeyCryptoConfig.LOCAL_KEY_DIR, tempDir.toString());
    }

    private static String key(int value) {
        byte[] key = new byte[BillingKeyCrypto.KEY_BYTES];
        Arrays.fill(key, (byte) value);
        return Base64.getEncoder().encodeToString(key);
    }
}
