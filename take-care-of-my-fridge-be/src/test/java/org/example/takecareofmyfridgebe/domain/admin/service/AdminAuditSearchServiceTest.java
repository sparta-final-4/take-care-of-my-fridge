package org.example.takecareofmyfridgebe.domain.admin.service;

import org.example.takecareofmyfridgebe.domain.admin.entity.AdminAuditLog;
import org.example.takecareofmyfridgebe.domain.admin.repository.AdminAuditLogRepository;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAuditSearchServiceTest {

    @Mock
    private AdminAuditLogRepository auditLogRepository;
    @InjectMocks
    private AdminAuditSearchService service;

    @Test
    void returnsPageWithActorRoleAtActionTime() {
        Member actor = Member.create("cook@example.com", "hash", "요리왕", Set.of());
        AdminAuditLog log = AdminAuditLog.create(actor, AdminAuditLog.Action.ROLE_CHANGE,
                "Member", 12L, AdminAuditLog.Result.DENIED);
        ReflectionTestUtils.setField(log, "id", 5L);
        ReflectionTestUtils.setField(log, "createdAt", LocalDateTime.of(2026, 10, 17, 10, 0));
        actor.promoteToAdmin();
        when(auditLogRepository.findAll(ArgumentMatchers.<Specification<AdminAuditLog>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(log), PageRequest.of(1, 10), 11));

        var response = service.search(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 17),
                AdminAuditLog.Action.ROLE_CHANGE, AdminAuditLog.Result.FAILED,
                " Member ", 12L, 2, 10);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(auditLogRepository).findAll(ArgumentMatchers.<Specification<AdminAuditLog>>any(), pageable.capture());
        assertEquals(1, pageable.getValue().getPageNumber());
        assertEquals(10, pageable.getValue().getPageSize());
        assertEquals(Sort.Direction.DESC, pageable.getValue().getSort().getOrderFor("createdAt").getDirection());
        assertEquals(2, response.pageNumber());
        assertEquals(11, response.totalElements());
        assertEquals(5L, response.content().get(0).auditLogId());
        assertEquals(Member.Role.USER, response.content().get(0).actorRole());
        assertEquals("cook@example.com", response.content().get(0).actorEmail());
    }

    @Test
    void rejectsReversedDateRangeBeforeQuery() {
        assertThrows(IllegalArgumentException.class,
                () -> service.search(LocalDate.of(2026, 10, 18), LocalDate.of(2026, 10, 17),
                        null, null, null, null, 1, 20));
        verifyNoInteractions(auditLogRepository);
    }
}
