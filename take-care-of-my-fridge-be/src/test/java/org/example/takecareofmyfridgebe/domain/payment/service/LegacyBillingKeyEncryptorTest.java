package org.example.takecareofmyfridgebe.domain.payment.service;

import org.example.takecareofmyfridgebe.domain.payment.entity.BillingKey;
import org.example.takecareofmyfridgebe.domain.payment.repository.BillingKeyRepository;
import org.example.takecareofmyfridgebe.global.config.jpa.JpaAuditingConfig;
import org.example.takecareofmyfridgebe.global.config.jpa.QueryDslConfig;
import org.example.takecareofmyfridgebe.support.MySqlContainerSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// 암호화 전 DB를 만들어 옮기기를 확인한다. 두 경우를 본다.
// - 손으로 DDL을 먼저 돌린 DB: 평문 billing_key가 NULL 허용으로 남고 새 컬럼도 NULL 허용
// - 배포 DB(ddl-auto: update): 평문 billing_key가 NOT NULL·UNIQUE로 남고, 새 컬럼은 NOT NULL에 기존 행은 '', 지문 UNIQUE는 못 만든 상태
// 컬럼을 바꾸는 DDL은 MySQL에서 바로 커밋되므로 테스트 트랜잭션을 끄고, 끝나면 테이블을 원래대로 되돌린다
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, QueryDslConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LegacyBillingKeyEncryptorTest extends MySqlContainerSupport {

    private static final BillingKeyCrypto CRYPTO = BillingKeyCryptoFixture.crypto();

    private final JdbcTemplate jdbcTemplate;
    private final BillingKeyRepository billingKeyRepository;
    private final LegacyBillingKeyEncryptor encryptor;

    private boolean legacySchema;

    @Autowired
    LegacyBillingKeyEncryptorTest(DataSource dataSource, BillingKeyRepository billingKeyRepository) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.billingKeyRepository = billingKeyRepository;
        this.encryptor = new LegacyBillingKeyEncryptor(jdbcTemplate, CRYPTO);
    }

    // 엔티티가 만든 원래 구조로 되돌린다: 평문 컬럼 없음, 새 컬럼 NOT NULL, 지문 UNIQUE 있음
    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM billing_keys");
        if (legacySchema) {
            jdbcTemplate.execute("ALTER TABLE billing_keys DROP COLUMN billing_key, "
                    + "MODIFY billing_key_ciphertext VARCHAR(512) NOT NULL, MODIFY billing_key_hash CHAR(64) NOT NULL");
            if (!hasUniqueOn("billing_key_hash")) {
                jdbcTemplate.execute("ALTER TABLE billing_keys ADD CONSTRAINT uk_billing_keys_billing_key_hash "
                        + "UNIQUE (billing_key_hash)");
            }
        }
    }

    @Test
    @DisplayName("배포 DB처럼 평문 컬럼이 NOT NULL·UNIQUE로 남고 새 컬럼이 ''로 채워져 있어도, 구조를 고쳐 옮기고 지문 UNIQUE를 만들며 새 카드를 넣을 수 있다")
    void migratesAndFixesSchema_whenLegacyColumnStillNotNullAndUnique() {
        toUpdatedByHibernateSchema();
        long first = insertLegacyWithEmptyNewColumns(7L, "billing-key-legacy-1");
        insertLegacyWithEmptyNewColumns(8L, "billing-key-legacy-2");

        int migrated = encryptor.migrate();

        assertThat(migrated).isEqualTo(2);
        assertThat(encryptor.countLegacyRows()).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT is_nullable FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = 'billing_keys' AND column_name = 'billing_key'",
                String.class)).isEqualTo("YES");
        assertThat(hasUniqueOn("billing_key")).isFalse();
        assertThat(hasUniqueOn("billing_key_hash")).isTrue();
        assertThat(CRYPTO.decrypt(billingKeyRepository.findByBillingKeyHash(CRYPTO.hash("billing-key-legacy-1"))
                .orElseThrow())).isEqualTo("billing-key-legacy-1");
        assertThat(billingKeyRepository.findById(first)).isPresent();

        // 평문 컬럼에 값을 넣지 않는 새 엔티티로 카드를 저장할 수 있다
        BillingKey saved = billingKeyRepository.saveAndFlush(BillingKey.builder().memberId(9L)
                .encryptedBillingKey(CRYPTO.encrypt(9L, "billing-key-new")).isDefault(true).build());
        assertThat(saved.getId()).isNotNull();
    }

    @Test
    @DisplayName("구조를 이미 고친 뒤 다시 돌려도 아무것도 바꾸지 않는다")
    void doesNothing_whenMigrateRunAgainAfterSchemaFixed() {
        toUpdatedByHibernateSchema();
        insertLegacyWithEmptyNewColumns(7L, "billing-key-legacy-1");
        encryptor.migrate();

        assertThat(encryptor.migrate()).isZero();
        assertThat(hasUniqueOn("billing_key_hash")).isTrue();
    }

    @Test
    @DisplayName("옮기지 못한 평문 행이 남으면 지문 UNIQUE를 만들지 않는다('' 중복으로 실패하지 않게)")
    void skipsHashUnique_whenLegacyRowsRemain() {
        toUpdatedByHibernateSchema();
        insertLegacyWithEmptyNewColumns(7L, "billing-key-legacy-1");
        // 컬럼(200자)에는 들어가지만 암호화 한도(200바이트)를 넘는 평문이라 이 행은 옮겨지지 않는다
        insertLegacyWithEmptyNewColumns(8L, "가".repeat(100));

        assertThat(encryptor.migrate()).isEqualTo(1);
        assertThat(encryptor.countLegacyRows()).isEqualTo(1);
        assertThat(hasUniqueOn("billing_key_hash")).isFalse();
    }

    @Test
    @DisplayName("평문 빌링키 행을 암호문과 지문으로 옮기고 평문을 지우며, 옮긴 카드는 지문으로 찾고 풀 수 있다")
    void encryptsLegacyRowsAndClearsPlaintext() {
        toLegacySchema();
        long first = insertLegacy(7L, "billing-key-legacy-1");
        long second = insertLegacy(8L, "billing-key-legacy-2");

        int migrated = encryptor.encryptLegacyRows();

        assertThat(migrated).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM billing_keys WHERE billing_key IS NOT NULL", Integer.class)).isZero();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT billing_key_ciphertext, billing_key_hash FROM billing_keys");
        assertThat(rows).allSatisfy(row -> {
            assertThat((String) row.get("billing_key_ciphertext")).startsWith("v1:k1:").doesNotContain("legacy");
            assertThat((String) row.get("billing_key_hash")).hasSize(64);
        });
        BillingKey found = billingKeyRepository.findByBillingKeyHash(CRYPTO.hash("billing-key-legacy-1"))
                .orElseThrow();
        assertThat(found.getId()).isEqualTo(first);
        assertThat(CRYPTO.decrypt(found)).isEqualTo("billing-key-legacy-1");
        assertThat(CRYPTO.decrypt(billingKeyRepository.findById(second).orElseThrow()))
                .isEqualTo("billing-key-legacy-2");
    }

    @Test
    @DisplayName("옮기기 전에는 남은 평문 행 수를 세고, 옮긴 뒤에는 0을 센다")
    void countsRemainingLegacyRows() {
        toLegacySchema();
        insertLegacy(7L, "billing-key-legacy-1");
        insertLegacy(8L, "billing-key-legacy-2");

        assertThat(encryptor.countLegacyRows()).isEqualTo(2);
        encryptor.encryptLegacyRows();
        assertThat(encryptor.countLegacyRows()).isZero();
    }

    @Test
    @DisplayName("실패 원인을 남길 때 예외 메시지(평문 빌링키가 들어갈 수 있음)는 빼고 SQL 상태·오류 코드를 남긴다")
    void describesSqlStateWithoutMessage() {
        RuntimeException e = new DataIntegrityViolationException("billing-key-legacy-1",
                new SQLException("billing-key-legacy-1", "23000", 1062));

        assertThat(LegacyBillingKeyEncryptor.describe(e))
                .contains("sqlState=23000", "errorCode=1062")
                .doesNotContain("billing-key-legacy-1");
    }

    @Test
    @DisplayName("다시 돌려도 이미 옮긴 행은 건드리지 않는다")
    void doesNothing_whenRunAgain() {
        toLegacySchema();
        insertLegacy(7L, "billing-key-legacy-1");
        encryptor.encryptLegacyRows();
        String ciphertext = jdbcTemplate.queryForObject("SELECT billing_key_ciphertext FROM billing_keys", String.class);

        assertThat(encryptor.encryptLegacyRows()).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT billing_key_ciphertext FROM billing_keys", String.class))
                .isEqualTo(ciphertext);
    }

    @Test
    @DisplayName("평문 컬럼이 없는 DB에서는 아무것도 하지 않는다")
    void doesNothing_whenLegacyColumnMissing() {
        assertThat(encryptor.encryptLegacyRows()).isZero();
    }

    // 옛 엔티티 그대로의 테이블에 Hibernate update가 새 컬럼만 더한 상태.
    // 평문 컬럼은 NOT NULL·UNIQUE, 지문 UNIQUE는 기존 행의 '' 중복 때문에 만들지 못했다
    private void toUpdatedByHibernateSchema() {
        jdbcTemplate.execute("ALTER TABLE billing_keys DROP INDEX uk_billing_keys_billing_key_hash");
        jdbcTemplate.execute("ALTER TABLE billing_keys ADD COLUMN billing_key VARCHAR(200) NOT NULL, "
                + "ADD CONSTRAINT uk_billing_keys_billing_key UNIQUE (billing_key)");
        legacySchema = true;
    }

    private long insertLegacyWithEmptyNewColumns(long memberId, String billingKey) {
        jdbcTemplate.update("INSERT INTO billing_keys (member_id, billing_key, billing_key_ciphertext, billing_key_hash, "
                + "is_default, status, created_at, updated_at) VALUES (?, ?, '', '', TRUE, 'ACTIVE', NOW(6), NOW(6))",
                memberId, billingKey);
        return jdbcTemplate.queryForObject("SELECT id FROM billing_keys WHERE billing_key = ?", Long.class, billingKey);
    }

    private boolean hasUniqueOn(String column) {
        return !jdbcTemplate.queryForList(LegacyBillingKeyEncryptor.SINGLE_COLUMN_UNIQUE_INDEXES, String.class, column)
                .isEmpty();
    }

    // 손으로 1단계 DDL을 적용한 상태: 새 컬럼은 NULL 허용, 옛 평문 컬럼은 NULL 허용으로 남음
    private void toLegacySchema() {
        jdbcTemplate.execute("ALTER TABLE billing_keys ADD COLUMN billing_key VARCHAR(200) NULL, "
                + "MODIFY billing_key_ciphertext VARCHAR(512) NULL, MODIFY billing_key_hash CHAR(64) NULL");
        legacySchema = true;
    }

    private long insertLegacy(long memberId, String billingKey) {
        jdbcTemplate.update("INSERT INTO billing_keys (member_id, billing_key, is_default, status, created_at, updated_at) "
                + "VALUES (?, ?, TRUE, 'ACTIVE', NOW(6), NOW(6))", memberId, billingKey);
        return jdbcTemplate.queryForObject("SELECT id FROM billing_keys WHERE billing_key = ?", Long.class, billingKey);
    }
}
