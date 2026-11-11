package org.example.takecareofmyfridgebe.domain.payment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.payment.entity.EncryptedBillingKey;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.List;
import java.util.function.BooleanSupplier;

// 암호화 전에 평문으로 저장한 빌링키(billing_keys.billing_key)를 기동할 때 암호문과 지문으로 옮기고 평문을 지운다.
// 옛 컬럼이 없으면(옮긴 뒤 컬럼을 지웠거나 처음부터 없는 DB) 아무것도 하지 않는다.
// 배포 DB는 ddl-auto: update라 Hibernate가 새 컬럼만 추가하고 옛 컬럼(NOT NULL·UNIQUE)은 그대로 둔다.
// 그대로면 평문을 지우지도, 새 카드를 넣지도 못하므로 옮기기 전에 옛 컬럼을 NULL 허용으로 바꾸고 옛 UNIQUE를 지운다.
// 새 컬럼은 기존 행에 ''로 채워져 지문 UNIQUE를 만들지 못하므로, 다 옮긴 뒤에 만든다.
// 각 단계는 지금 구조를 보고 필요할 때만 한다. 손으로 DDL을 먼저 돌렸거나, 다시 돌거나, 여러 서버가 같이 돌아 다른 서버가 먼저 바꿨어도 결과가 같다.
// 옛 컬럼 자체는 지우지 않는다(되돌릴 수 없는 DDL은 배포와 따로 한다). 옮긴 행의 평문은 지워지므로 옛 코드로 되돌려도 그 카드는 쓸 수 없다.
// 행마다 "평문이 아직 그 값일 때만" 바꾸므로 여러 서버가 같이 돌거나 다시 돌아도 결과가 같다.
// 웹 서버가 요청을 받기 전(빈 생성이 끝난 직후)에 돌아, 옮기는 중에 옛 카드로 결제·등록 요청이 들어오지 않게 한다.
// 실패해도 서버 기동은 막지 않는다. 옮기지 못한 카드는 평문이 남아 있어 다음 기동 때 다시 옮긴다
@Slf4j
@Component
@RequiredArgsConstructor
public class LegacyBillingKeyEncryptor implements SmartInitializingSingleton {

    static final String LEGACY_COLUMN_EXISTS = "SELECT COUNT(*) FROM information_schema.columns "
            + "WHERE table_schema = DATABASE() AND table_name = 'billing_keys' AND column_name = 'billing_key'";
    static final String LEGACY_COLUMN_NOT_NULL_TYPE = "SELECT column_type, character_set_name, collation_name "
            + "FROM information_schema.columns "
            + "WHERE table_schema = DATABASE() AND table_name = 'billing_keys' AND column_name = 'billing_key' "
            + "AND is_nullable = 'NO'";
    // 컬럼 하나만으로 된 UNIQUE 인덱스 이름. 이름이 문서와 다르게 만들어졌어도 찾는다
    static final String SINGLE_COLUMN_UNIQUE_INDEXES = "SELECT index_name FROM information_schema.statistics "
            + "WHERE table_schema = DATABASE() AND table_name = 'billing_keys' AND non_unique = 0 "
            + "GROUP BY index_name HAVING COUNT(*) = 1 AND MAX(column_name) = ?";
    static final String HASH_UNIQUE_NAME = "uk_billing_keys_billing_key_hash";
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
            migrate();
        } catch (RuntimeException e) {
            log.error("평문 빌링키 암호화 실패, 다음 기동 때 다시 시도: {}", describe(e));
        }
    }

    // 구조 정리 → 행 옮기기 → (다 옮겼으면) 지문 UNIQUE. 옮긴 행 수를 돌려준다
    int migrate() {
        if (!hasLegacyColumn()) {
            return 0;
        }
        prepareLegacySchema();
        int migrated = encryptLegacyRows();
        if (migrated > 0) {
            log.info("평문 빌링키를 암호화했습니다: {}건", migrated);
        }
        int remaining = countLegacyRows();
        if (remaining > 0) {
            log.error("평문 빌링키가 남아 있습니다. 이 카드들은 결제·삭제에 쓸 수 없고 다음 기동 때 다시 옮깁니다: {}건", remaining);
            return migrated;
        }
        ensureHashUnique();
        return migrated;
    }

    // 옛 컬럼을 NULL 허용으로 바꾸고(평문을 지우고 새 카드를 넣을 수 있게) 옛 컬럼의 UNIQUE를 지운다
    // 타입과 문자셋·정렬 규칙은 원래 정의를 그대로 쓰고 NULL 허용만 바꾼다
    void prepareLegacySchema() {
        List<String> notNullDefinition = jdbcTemplate.query(LEGACY_COLUMN_NOT_NULL_TYPE, (rs, rowNum) ->
                rs.getString("column_type")
                        + (rs.getString("character_set_name") == null ? "" : " CHARACTER SET " + rs.getString("character_set_name"))
                        + (rs.getString("collation_name") == null ? "" : " COLLATE " + rs.getString("collation_name")));
        if (!notNullDefinition.isEmpty()) {
            executeDdl("ALTER TABLE billing_keys MODIFY billing_key " + notNullDefinition.get(0) + " NULL",
                    () -> jdbcTemplate.queryForList(LEGACY_COLUMN_NOT_NULL_TYPE).isEmpty());
            log.info("평문 빌링키 컬럼을 NULL 허용으로 바꿨습니다.");
        }
        for (String index : singleColumnUniqueIndexes("billing_key")) {
            executeDdl("ALTER TABLE billing_keys DROP INDEX " + quote(index),
                    () -> !singleColumnUniqueIndexes("billing_key").contains(index));
            log.info("평문 빌링키 컬럼의 UNIQUE를 지웠습니다: {}", index);
        }
    }

    // 지문이 모두 채워진 뒤에만 부른다. 이미 있으면(이름이 달라도) 만들지 않는다
    void ensureHashUnique() {
        if (singleColumnUniqueIndexes("billing_key_hash").isEmpty()) {
            executeDdl("ALTER TABLE billing_keys ADD CONSTRAINT " + HASH_UNIQUE_NAME + " UNIQUE (billing_key_hash)",
                    () -> !singleColumnUniqueIndexes("billing_key_hash").isEmpty());
            log.info("빌링키 지문 UNIQUE를 만들었습니다: {}", HASH_UNIQUE_NAME);
        }
    }

    // 다른 서버가 같은 DDL을 먼저 끝내 실패했으면(이미 없는 인덱스 지우기 등) 원하는 상태인지 다시 보고 넘어간다
    private void executeDdl(String sql, BooleanSupplier done) {
        try {
            jdbcTemplate.execute(sql);
        } catch (RuntimeException e) {
            if (!done.getAsBoolean()) {
                throw e;
            }
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

    private List<String> singleColumnUniqueIndexes(String column) {
        return jdbcTemplate.queryForList(SINGLE_COLUMN_UNIQUE_INDEXES, String.class, column);
    }

    // information_schema에서 읽은 인덱스 이름이라 외부 입력은 아니지만, 식별자로 넣으므로 백틱으로 감싼다
    private static String quote(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
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
