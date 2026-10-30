package org.example.takecareofmyfridgebe.domain.admin.service;

import org.example.takecareofmyfridgebe.domain.admin.entity.AdminAuditLog;
import org.example.takecareofmyfridgebe.domain.admin.repository.AdminAuditLogRepository;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.member.repository.MemberRepository;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class AdminAuditLogServiceTest {

    @Mock
    private AdminAuditLogRepository auditLogRepository;
    @Mock
    private MemberRepository memberRepository;
    @InjectMocks
    private AdminAuditLogService service;

    @Test
    void recordsSuccessfulActionWithActorAndTarget() {
        Member actor = Member.builder()
                .email("admin@example.com")
                .passwordHash("hash")
                .nickname("관리자")
                .role(Member.Role.ADMIN)
                .build();
        when(memberRepository.findById(7L)).thenReturn(Optional.of(actor));

        service.record(7L, AdminAuditLog.Action.ROLE_CHANGE, "Member", 12L,
                AdminAuditLog.Result.SUCCESS);

        ArgumentCaptor<AdminAuditLog> captor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AdminAuditLog log = captor.getValue();
        assertSame(actor, log.getMember());
        assertEquals(Member.Role.ADMIN, log.getActorRole());
        assertEquals(AdminAuditLog.Action.ROLE_CHANGE, log.getAction());
        assertEquals("Member", log.getTargetType());
        assertEquals(12L, log.getTargetId());
        assertEquals(AdminAuditLog.Result.SUCCESS, log.getResult());
    }

    @Test
    void recordsBeforeAndAfterValues() {
        Member actor = Member.builder()
                .email("admin@example.com")
                .passwordHash("hash")
                .nickname("관리자")
                .role(Member.Role.ADMIN)
                .build();
        when(memberRepository.findById(7L)).thenReturn(Optional.of(actor));

        service.record(7L, AdminAuditLog.Action.ROLE_CHANGE, AdminAuditLog.TARGET_MEMBER, 12L,
                AdminAuditLog.Result.SUCCESS, Map.of("role", "USER"), Map.of("role", "ADMIN"));

        ArgumentCaptor<AdminAuditLog> captor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertEquals(Map.of("role", "USER"), captor.getValue().getBeforeValue());
        assertEquals(Map.of("role", "ADMIN"), captor.getValue().getAfterValue());
    }

    @Test
    void recordsDeniedActionByUserWithoutTargetId() {
        Member actor = Member.builder()
                .email("user@example.com")
                .passwordHash("hash")
                .nickname("회원")
                .build();
        when(memberRepository.findById(9L)).thenReturn(Optional.of(actor));

        service.record(9L, AdminAuditLog.Action.PRODUCT_DELETE, "Product", null,
                AdminAuditLog.Result.DENIED);

        ArgumentCaptor<AdminAuditLog> captor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertSame(actor, captor.getValue().getMember());
        assertEquals(Member.Role.USER, captor.getValue().getActorRole());
        assertNull(captor.getValue().getTargetId());
        assertEquals(AdminAuditLog.Result.DENIED, captor.getValue().getResult());
    }

    @Test
    void rejectsUnknownActorWithoutWritingLog() {
        when(memberRepository.findById(99L)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.record(99L, AdminAuditLog.Action.PRICE_UPDATE, "Product", 3L,
                        AdminAuditLog.Result.FAILED));

        assertEquals(ErrorCode.MEMBER_NOT_FOUND, exception.getErrorCode());
        verify(auditLogRepository, never()).save(any(AdminAuditLog.class));
    }
}
