package org.example.takecareofmyfridgebe.domain.admin.repository;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import org.example.takecareofmyfridgebe.domain.admin.entity.AdminAuditLog;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.global.config.JpaAuditingConfig;
import org.example.takecareofmyfridgebe.global.config.QueryDslConfig;
import org.example.takecareofmyfridgebe.support.MySqlContainerSupport;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, QueryDslConfig.class})
class AdminAuditLogRepositoryTest extends MySqlContainerSupport {

    @Autowired private EntityManager entityManager;
    @Autowired private AdminAuditLogRepository auditLogRepository;

    @Test
    void searchLoadsActorWithLog() {
        for (int index = 1; index <= 2; index++) {
            Member member = Member.builder().email("admin" + index + "@example.com").passwordHash("hash")
                    .nickname("관리자" + index).phone("0101111000" + index).build();
            entityManager.persist(member);
            entityManager.persist(AdminAuditLog.builder().member(member)
                    .action(AdminAuditLog.Action.ROLE_CHANGE).targetType(AdminAuditLog.TARGET_MEMBER)
                    .targetId(1L).result(AdminAuditLog.Result.SUCCESS).build());
        }
        entityManager.flush();
        entityManager.clear();

        var page = auditLogRepository.findAll(
                (Specification<AdminAuditLog>) (root, query, cb) -> cb.conjunction(), PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(2)
                .allSatisfy(log -> assertThat(Hibernate.isInitialized(log.getMember())).isTrue());
    }
}
