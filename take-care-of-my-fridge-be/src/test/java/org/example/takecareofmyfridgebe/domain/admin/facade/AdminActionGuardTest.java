package org.example.takecareofmyfridgebe.domain.admin.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.example.takecareofmyfridgebe.domain.admin.entity.AdminAuditLog;
import org.example.takecareofmyfridgebe.domain.admin.service.AdminAuditLogService;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.junit.jupiter.api.Test;

class AdminActionGuardTest {

    private final AdminAuditLogService audit = mock(AdminAuditLogService.class);
    private final AdminActionGuard guard = new AdminActionGuard(audit);

    @Test
    void returnsResultWithoutAuditOnSuccess() {
        assertThat(guard.run(7L, Member.Role.ADMIN, AdminAuditLog.Action.ROLE_CHANGE,
                AdminAuditLog.TARGET_MEMBER, 12L, () -> "ok")).isEqualTo("ok");
        verifyNoInteractions(audit);
    }

    @Test
    void recordsDeniedWithoutRunningWork() {
        assertThatThrownBy(() -> guard.run(7L, Member.Role.USER, AdminAuditLog.Action.ROLE_CHANGE,
                AdminAuditLog.TARGET_MEMBER, 12L, () -> {
                    throw new AssertionError("실행되면 안 된다");
                }))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.ADMIN_ACCESS_DENIED));
        verify(audit).record(7L, AdminAuditLog.Action.ROLE_CHANGE,
                AdminAuditLog.TARGET_MEMBER, 12L, AdminAuditLog.Result.DENIED);
    }

    @Test
    void recordsUnexpectedFailureAndRethrows() {
        IllegalStateException failure = new IllegalStateException("lock timeout");

        assertThatThrownBy(() -> guard.run(7L, Member.Role.ADMIN, AdminAuditLog.Action.ROLE_CHANGE,
                AdminAuditLog.TARGET_MEMBER, 12L, () -> {
                    throw failure;
                })).isSameAs(failure);
        verify(audit).record(7L, AdminAuditLog.Action.ROLE_CHANGE,
                AdminAuditLog.TARGET_MEMBER, 12L, AdminAuditLog.Result.FAILED);
    }
}
