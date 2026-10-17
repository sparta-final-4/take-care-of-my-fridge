package org.example.takecareofmyfridgebe.domain.admin.entity;

import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdminAuditLogTest {

    @Test
    void roleChangeStoresOnlyRoleValues() {
        Member actor = Member.builder()
                .email("admin@example.com")
                .passwordHash("hash")
                .nickname("관리자")
                .role(Member.Role.ADMIN)
                .build();
        AdminAuditLog log = AdminAuditLog.builder()
                .member(actor)
                .action(AdminAuditLog.Action.ROLE_CHANGE)
                .targetType("Member")
                .targetId(12L)
                .result(AdminAuditLog.Result.SUCCESS)
                .beforeValue(Map.of("role", "USER"))
                .afterValue(Map.of("role", "ADMIN"))
                .build();

        assertEquals(Map.of("role", "USER"), log.getBeforeValue());
        assertEquals(Map.of("role", "ADMIN"), log.getAfterValue());
    }
}
