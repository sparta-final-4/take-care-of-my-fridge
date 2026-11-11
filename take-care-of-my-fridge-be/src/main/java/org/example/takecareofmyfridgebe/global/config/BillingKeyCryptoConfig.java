package org.example.takecareofmyfridgebe.global.config;

import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.payment.service.BillingKeyCrypto;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

// 빌링키 암호화 키와 지문 키.
// - 배포 서버(prod 프로필)는 Parameter Store /imhungry/<APP_ENV>/ 의 아래 값을 읽는다. 하나라도 없거나 형식이 틀리면 기동에 실패한다.
//   BILLING_KEY_ENCRYPTION_KEY        지금 쓰는 암호화 키(32바이트를 Base64로)
//   BILLING_KEY_HMAC_KEY              지문 키(32바이트 이상을 Base64로). 바꾸면 기존 지문과 맞지 않으므로 바꾸지 않는다
//   BILLING_KEY_ENCRYPTION_KEY_ID     지금 키의 ID. 없으면 k1
//   BILLING_KEY_RETIRED_ENCRYPTION_KEYS  키를 바꾼 뒤 예전 암호문을 풀 키들. "k1=Base64,k2=Base64". 없으면 비움
// - 그 밖(로컬 실행·테스트)에서 값이 없으면 ~/.imhungry/billing-key/ 아래에 키 파일을 한 번 만들어 계속 쓴다.
//   키를 직접 만들거나 넣지 않아도 로컬에서 카드 등록·결제를 확인할 수 있다. 파일을 지우면 그전에 등록한 로컬 카드는 풀 수 없다
@Slf4j
@Configuration
public class BillingKeyCryptoConfig {

    static final String ENCRYPTION_KEY = "BILLING_KEY_ENCRYPTION_KEY";
    static final String ENCRYPTION_KEY_ID = "BILLING_KEY_ENCRYPTION_KEY_ID";
    static final String RETIRED_ENCRYPTION_KEYS = "BILLING_KEY_RETIRED_ENCRYPTION_KEYS";
    static final String HMAC_KEY = "BILLING_KEY_HMAC_KEY";
    static final String LOCAL_KEY_DIR = "BILLING_KEY_LOCAL_KEY_DIR";

    private static final String DEPLOY_PROFILE = "prod";
    private static final String DEFAULT_KEY_ID = "k1";
    private static final String ENCRYPTION_KEY_FILE = "encryption.key";
    private static final String HMAC_KEY_FILE = "hmac.key";

    @Bean
    public BillingKeyCrypto billingKeyCrypto(Environment environment) {
        boolean deployed = environment.matchesProfiles(DEPLOY_PROFILE);
        Path localKeyDir = localKeyDir(environment);
        String keyId = environment.getProperty(ENCRYPTION_KEY_ID, DEFAULT_KEY_ID).trim();

        Map<String, byte[]> encryptionKeys = new LinkedHashMap<>(
                parseRetiredKeys(environment.getProperty(RETIRED_ENCRYPTION_KEYS, "")));
        // 예전 키와 지금 키의 ID가 같으면 예전 키가 덮여 기존 암호문을 못 푼다. 기동 때 막는다
        if (encryptionKeys.containsKey(keyId)) {
            throw new IllegalStateException(RETIRED_ENCRYPTION_KEYS + "에 지금 키 ID(" + keyId
                    + ")가 있습니다. 키를 바꿀 때는 " + ENCRYPTION_KEY_ID + "도 새 ID로 바꿔야 합니다.");
        }
        byte[] encryptionKey = readKey(environment, ENCRYPTION_KEY, deployed, localKeyDir.resolve(ENCRYPTION_KEY_FILE));
        encryptionKeys.put(keyId, encryptionKey);
        byte[] hmacKey = readKey(environment, HMAC_KEY, deployed, localKeyDir.resolve(HMAC_KEY_FILE));
        return new BillingKeyCrypto(keyId, encryptionKeys, hmacKey);
    }

    // 설정 값이 있으면 그 값을 쓴다. 없으면 배포 서버는 기동을 멈추고, 로컬은 키 파일을 쓴다(없으면 만든다)
    static byte[] readKey(Environment environment, String name, boolean deployed, Path localFile) {
        String value = environment.getProperty(name, "");
        if (!value.isBlank()) {
            return decode(name, value);
        }
        if (deployed) {
            throw new IllegalStateException(name + " 설정이 없습니다. Parameter Store에 등록해야 합니다.");
        }
        return decode(name, loadOrCreateLocalKey(localFile));
    }

    static Map<String, byte[]> parseRetiredKeys(String value) {
        Map<String, byte[]> keys = new LinkedHashMap<>();
        if (value == null || value.isBlank()) {
            return keys;
        }
        for (String entry : value.split(",")) {
            String[] parts = entry.trim().split("=", 2);
            if (parts.length != 2 || parts[0].isBlank()) {
                throw new IllegalStateException(RETIRED_ENCRYPTION_KEYS + " 형식은 \"키ID=Base64,키ID=Base64\"입니다.");
            }
            keys.put(parts[0].trim(), decode(RETIRED_ENCRYPTION_KEYS, parts[1].trim()));
        }
        return keys;
    }

    private static byte[] decode(String name, String value) {
        try {
            return Base64.getDecoder().decode(value.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(name + " 값이 Base64가 아닙니다.");
        }
    }

    // 임시 파일에 다 쓴 뒤 이름을 바꿔, 다른 쪽이 반쯤 쓴 파일을 읽지 않게 한다.
    // 여러 테스트·서버가 동시에 만들려 해도 먼저 자리 잡은 파일 하나만 쓴다
    private static String loadOrCreateLocalKey(Path file) {
        try {
            if (Files.notExists(file)) {
                Files.createDirectories(file.getParent());
                byte[] key = new byte[BillingKeyCrypto.KEY_BYTES];
                new SecureRandom().nextBytes(key);
                Path temp = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".tmp");
                try {
                    restrictToOwner(temp);
                    Files.writeString(temp, Base64.getEncoder().encodeToString(key), StandardCharsets.UTF_8,
                            StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                    Files.move(temp, file);
                    log.info("로컬 빌링키 키 파일을 만들었습니다: {}", file);
                } catch (FileAlreadyExistsException ignored) {
                    // 다른 쪽이 먼저 만들었다. 그 파일을 읽는다
                } finally {
                    Files.deleteIfExists(temp);
                }
            }
            return Files.readString(file, StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new UncheckedIOException("로컬 빌링키 키 파일을 읽거나 만들지 못했습니다: " + file, e);
        }
    }

    // POSIX 파일 시스템이면 본인만 읽게 한다. Windows는 사용자 폴더 권한을 그대로 따른다
    private static void restrictToOwner(Path file) throws IOException {
        if (file.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        }
    }

    private static Path localKeyDir(Environment environment) {
        String configured = environment.getProperty(LOCAL_KEY_DIR, "");
        if (!configured.isBlank()) {
            return Path.of(configured);
        }
        return Path.of(System.getProperty("user.home"), ".imhungry", "billing-key");
    }
}
