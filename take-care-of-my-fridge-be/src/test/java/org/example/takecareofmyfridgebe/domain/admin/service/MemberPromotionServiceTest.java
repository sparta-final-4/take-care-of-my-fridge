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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberPromotionServiceTest {

    @Mock
    private MemberRepository memberRepository;
    @Mock
    private AdminAuditLogRepository auditLogRepository;
    @InjectMocks
    private MemberPromotionService service;

    @Test
    void promotesUserAndWritesRoleChangeInSameOperation() {
        Member actor = Member.builder()
                .email("admin@example.com")
                .passwordHash("hash")
                .nickname("관리자")
                .role(Member.Role.ADMIN)
                .build();
        Member target = Member.builder()
                .email("cook@example.com")
                .passwordHash("hash")
                .nickname("요리왕")
                .build();
        when(memberRepository.findById(1L)).thenReturn(Optional.of(actor));
        when(memberRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(target));

        service.promote(1L, 2L);

        assertEquals(Member.Role.ADMIN, target.getRole());
        ArgumentCaptor<AdminAuditLog> captor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertEquals(AdminAuditLog.Action.ROLE_CHANGE, captor.getValue().getAction());
        assertEquals(AdminAuditLog.Result.SUCCESS, captor.getValue().getResult());
        assertEquals(Map.of("role", "USER"), captor.getValue().getBeforeValue());
        assertEquals(Map.of("role", "ADMIN"), captor.getValue().getAfterValue());
    }

    @Test
    void rejectsMissingSuspendedAndAlreadyAdminWithoutSuccessLog() {
        Member actor = Member.builder()
                .email("admin@example.com")
                .passwordHash("hash")
                .nickname("관리자")
                .role(Member.Role.ADMIN)
                .build();
        when(memberRepository.findById(1L)).thenReturn(Optional.of(actor));
        when(memberRepository.findByIdForUpdate(2L)).thenReturn(Optional.empty());
        assertError(ErrorCode.MEMBER_NOT_FOUND);

        Member suspended = Member.builder()
                .email("suspended@example.com")
                .passwordHash("hash")
                .nickname("정지회원")
                .build();
        ReflectionTestUtils.setField(suspended, "status", Member.Status.SUSPENDED);
        when(memberRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(suspended));
        assertError(ErrorCode.PROMOTION_SUSPENDED);

        Member admin = Member.builder()
                .email("other@example.com")
                .passwordHash("hash")
                .nickname("다른관리자")
                .role(Member.Role.ADMIN)
                .build();
        when(memberRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(admin));
        assertError(ErrorCode.ALREADY_ADMIN);
        verify(auditLogRepository, never()).save(any(AdminAuditLog.class));
    }

    private void assertError(ErrorCode expected) {
        BusinessException error = assertThrows(BusinessException.class, () -> service.promote(1L, 2L));
        assertEquals(expected, error.getErrorCode());
    }
}
