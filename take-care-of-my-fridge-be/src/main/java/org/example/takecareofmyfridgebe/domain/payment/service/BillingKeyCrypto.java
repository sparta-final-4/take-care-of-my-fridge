package org.example.takecareofmyfridgebe.domain.payment.service;

import org.example.takecareofmyfridgebe.domain.payment.entity.BillingKey;
import org.example.takecareofmyfridgebe.domain.payment.entity.EncryptedBillingKey;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

// 빌링키를 DB에 평문으로 두지 않기 위한 암호화와 지문.
// - 암호문: AES-256-GCM. IV를 매번 새로 만들어 같은 빌링키도 저장할 때마다 다른 값이 된다.
//   회원 ID를 추가 인증 데이터(AAD)로 묶어, 다른 회원 행에 옮겨 붙인 암호문은 풀리지 않는다.
//   형식은 "v1:{키 ID}:{Base64(IV + 암호문 + 태그)}"라 암호화 키를 바꿔도 예전 키로 만든 암호문을 그대로 풀 수 있다.
// - 지문: HMAC-SHA256(16진수 64자). 같은 빌링키는 늘 같은 값이라 UNIQUE와 같은 빌링키 조회에 쓴다.
//   지문 키는 바꾸면 기존 지문과 맞지 않으므로 바꾸지 않는다.
// 빌링키 원문과 키는 예외 메시지·로그에 넣지 않는다
public class BillingKeyCrypto {

    public static final int KEY_BYTES = 32;
    static final String FORMAT_VERSION = "v1";

    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String AAD_PREFIX = "billing_keys.member_id=";
    private static final Pattern KEY_ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,16}");

    private final SecureRandom secureRandom = new SecureRandom();
    private final String currentKeyId;
    private final Map<String, SecretKey> encryptionKeys;
    private final SecretKey hmacKey;

    // encryptionKeys에는 지금 쓰는 키와 예전 키(복호화만)가 키 ID별로 들어 있다
    public BillingKeyCrypto(String currentKeyId, Map<String, byte[]> encryptionKeys, byte[] hmacKey) {
        validateKeyId(currentKeyId);
        if (!encryptionKeys.containsKey(currentKeyId)) {
            throw new IllegalArgumentException("지금 쓰는 빌링키 암호화 키가 없습니다: keyId=" + currentKeyId);
        }
        Map<String, SecretKey> keys = new LinkedHashMap<>();
        encryptionKeys.forEach((keyId, key) -> {
            validateKeyId(keyId);
            if (key == null || key.length != KEY_BYTES) {
                throw new IllegalArgumentException("빌링키 암호화 키는 32바이트여야 합니다: keyId=" + keyId);
            }
            keys.put(keyId, new SecretKeySpec(key, "AES"));
        });
        if (hmacKey == null || hmacKey.length < KEY_BYTES) {
            throw new IllegalArgumentException("빌링키 지문 키는 32바이트 이상이어야 합니다.");
        }
        this.currentKeyId = currentKeyId;
        this.encryptionKeys = Map.copyOf(keys);
        this.hmacKey = new SecretKeySpec(hmacKey, HMAC_ALGORITHM);
    }

    public EncryptedBillingKey encrypt(Long memberId, String billingKey) {
        validatePlaintext(memberId, billingKey);
        byte[] iv = new byte[IV_BYTES];
        secureRandom.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKeys.get(currentKeyId), new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad(memberId));
            byte[] sealed = cipher.doFinal(billingKey.getBytes(StandardCharsets.UTF_8));
            byte[] payload = ByteBuffer.allocate(iv.length + sealed.length).put(iv).put(sealed).array();
            String ciphertext = FORMAT_VERSION + ":" + currentKeyId + ":" + Base64.getEncoder().encodeToString(payload);
            return new EncryptedBillingKey(ciphertext, hash(billingKey));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("빌링키를 암호화하지 못했습니다.", e);
        }
    }

    // PortOne을 부르기 직전에만 푼다
    public String decrypt(BillingKey card) {
        return decrypt(card.getMemberId(), card.getBillingKeyCiphertext());
    }

    public String decrypt(Long memberId, String ciphertext) {
        if (memberId == null || ciphertext == null) {
            throw new IllegalStateException("복호화할 빌링키가 없습니다.");
        }
        String[] parts = ciphertext.split(":", 3);
        if (parts.length != 3 || !FORMAT_VERSION.equals(parts[0])) {
            throw new IllegalStateException("빌링키 암호문 형식이 올바르지 않습니다.");
        }
        SecretKey key = encryptionKeys.get(parts[1]);
        if (key == null) {
            throw new IllegalStateException("빌링키 암호문의 키를 찾을 수 없습니다: keyId=" + parts[1]);
        }
        byte[] payload;
        try {
            payload = Base64.getDecoder().decode(parts[2]);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("빌링키 암호문 형식이 올바르지 않습니다.");
        }
        if (payload.length <= IV_BYTES) {
            throw new IllegalStateException("빌링키 암호문 형식이 올바르지 않습니다.");
        }
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, payload, 0, IV_BYTES));
            cipher.updateAAD(aad(memberId));
            byte[] plain = cipher.doFinal(payload, IV_BYTES, payload.length - IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (AEADBadTagException e) {
            // 키가 다르거나, 암호문이 바뀌었거나, 다른 회원 행의 암호문이다. 원인은 메시지에 남기지 않는다
            throw new IllegalStateException("빌링키 암호문을 검증하지 못했습니다: keyId=" + parts[1]);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("빌링키를 복호화하지 못했습니다: keyId=" + parts[1]);
        }
    }

    public String hash(String billingKey) {
        if (billingKey == null || billingKey.isBlank()) {
            throw new IllegalArgumentException("지문을 만들 빌링키가 없습니다.");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(hmacKey);
            return HexFormat.of().formatHex(mac.doFinal(billingKey.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("빌링키 지문을 만들지 못했습니다.", e);
        }
    }

    private static byte[] aad(Long memberId) {
        return (AAD_PREFIX + memberId).getBytes(StandardCharsets.UTF_8);
    }

    private static void validatePlaintext(Long memberId, String billingKey) {
        if (memberId == null) {
            throw new IllegalArgumentException("빌링키 주인 회원 ID가 없습니다.");
        }
        if (billingKey == null || billingKey.isBlank()) {
            throw new IllegalArgumentException("암호화할 빌링키가 없습니다.");
        }
        // 암호문이 billing_key_ciphertext 길이 안에 들어가도록 바이트 수로 막는다
        if (billingKey.getBytes(StandardCharsets.UTF_8).length > BillingKey.MAX_BILLING_KEY_BYTES) {
            throw new IllegalArgumentException("빌링키가 너무 깁니다.");
        }
    }

    private static void validateKeyId(String keyId) {
        if (keyId == null || !KEY_ID_PATTERN.matcher(keyId).matches()) {
            throw new IllegalArgumentException("빌링키 암호화 키 ID는 영문·숫자·_·- 1~16자여야 합니다.");
        }
    }
}
