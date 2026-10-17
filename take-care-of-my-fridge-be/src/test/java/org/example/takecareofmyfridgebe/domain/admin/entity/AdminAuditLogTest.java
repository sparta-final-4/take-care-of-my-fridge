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
        AdminAuditLog log = AdminAuditLog.create(actor, AdminAuditLog.Action.ROLE_CHANGE,
                "Member", 12L, AdminAuditLog.Result.SUCCESS,
                Map.of("role", "USER"), Map.of("role", "ADMIN"));

        assertEquals(Map.of("role", "USER"), log.getBeforeValue());
        assertEquals(Map.of("role", "ADMIN"), log.getAfterValue());
    }
}
