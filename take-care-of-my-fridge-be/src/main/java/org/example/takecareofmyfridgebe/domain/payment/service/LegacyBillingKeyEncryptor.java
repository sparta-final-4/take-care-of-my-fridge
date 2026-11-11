package org.example.takecareofmyfridgebe.domain.payment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.payment.entity.EncryptedBillingKey;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.List;

// 암호화 전에 평문으로 저장한 빌링키(billing_keys.billing_key)를 기동할 때 암호문과 지문으로 옮기고 평문을 지운다.
// 옛 컬럼이 없으면(옮긴 뒤 컬럼을 지웠거나 처음부터 없는 DB) 아무것도 하지 않는다.
// 행마다 "평문이 아직 그 값일 때만" 바꾸므로 여러 서버가 같이 돌거나 다시 돌아도 결과가 같다.
// 웹 서버가 요청을 받기 전(빈 생성이 끝난 직후)에 돌아, 옮기는 중에 옛 카드로 결제·등록 요청이 들어오지 않게 한다.
// 실패해도 서버 기동은 막지 않는다. 옮기지 못한 카드는 평문이 남아 있어 다음 기동 때 다시 옮긴다
@Slf4j
@Component
@RequiredArgsConstructor
public class LegacyBillingKeyEncryptor implements SmartInitializingSingleton {

    static final String LEGACY_COLUMN_EXISTS = "SELECT COUNT(*) FROM information_schema.columns "
            + "WHERE table_schema = DATABASE() AND table_name = 'billing_keys' AND column_name = 'billing_key'";
    static final String SELECT_LEGACY_ROWS =
            "SELECT id, member_id, billing_key FROM billing_keys WHERE billing_key IS NOT NULL ORDER BY id";
    static final String COUNT_LEGACY_ROWS = "SELECT COUNT(*) FROM billing_keys WHERE billing_key IS NOT NULL";
    static final String UPDATE_ROW = "UPDATE billing_keys SET billing_key_ciphertext = ?, billing_key_hash = ?, "
            + "billing_key = NULL WHERE id = ? AND billing_key = ?";

    private final JdbcTemplate jdbcTemplate;
    private final BillingKeyCrypto billingKeyCrypto;

    @Override
    public void afterSingletonsInstantiated() {
        try {
            int migrated = encryptLegacyRows();
            if (migrated > 0) {
                log.info("평문 빌링키를 암호화했습니다: {}건", migrated);
            }
            int remaining = countLegacyRows();
            if (remaining > 0) {
                log.error("평문 빌링키가 남아 있습니다. 이 카드들은 결제·삭제에 쓸 수 없고 다음 기동 때 다시 옮깁니다: {}건", remaining);
            }
        } catch (RuntimeException e) {
            log.error("평문 빌링키 암호화 실패, 다음 기동 때 다시 시도: {}", describe(e));
        }
    }

    // 옮긴 행 수를 돌려준다
    public int encryptLegacyRows() {
        if (!hasLegacyColumn()) {
            return 0;
        }
        List<LegacyRow> rows = jdbcTemplate.query(SELECT_LEGACY_ROWS, (rs, rowNum) ->
                new LegacyRow(rs.getLong("id"), rs.getLong("member_id"), rs.getString("billing_key")));
        int migrated = 0;
        for (LegacyRow row : rows) {
            // 한 행이 실패해도 나머지는 옮긴다. 실패한 행은 평문이 남아 다음 기동 때 다시 옮긴다
            try {
                EncryptedBillingKey encrypted = billingKeyCrypto.encrypt(row.memberId(), row.billingKey());
                migrated += jdbcTemplate.update(UPDATE_ROW, encrypted.ciphertext(), encrypted.hash(), row.id(),
                        row.billingKey());
            } catch (RuntimeException e) {
                log.error("평문 빌링키 암호화 실패: cardId={}, error={}", row.id(), describe(e));
            }
        }
        return migrated;
    }

    // 옛 컬럼이 없으면 0
    int countLegacyRows() {
        if (!hasLegacyColumn()) {
            return 0;
        }
        Integer count = jdbcTemplate.queryForObject(COUNT_LEGACY_ROWS, Integer.class);
        return count == null ? 0 : count;
    }

    // 예외 메시지에는 SQL 파라미터(평문 빌링키)가 들어갈 수 있어 남기지 않는다.
    // 대신 원인을 좇을 수 있게 예외 종류와 DB가 준 SQL 상태·오류 코드를 남긴다
    static String describe(RuntimeException e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql) {
                return e.getClass().getName() + " (sqlState=" + sql.getSQLState() + ", errorCode=" + sql.getErrorCode() + ")";
            }
        }
        return e.getClass().getName();
    }

    private boolean hasLegacyColumn() {
        Integer count = jdbcTemplate.queryForObject(LEGACY_COLUMN_EXISTS, Integer.class);
        return count != null && count > 0;
    }

    private record LegacyRow(long id, long memberId, String billingKey) {

        // record 기본 toString이 평문 빌링키를 찍지 않게 한다
        @Override
        public String toString() {
            return "LegacyRow[id=" + id + ", memberId=" + memberId + "]";
        }
    }
}
