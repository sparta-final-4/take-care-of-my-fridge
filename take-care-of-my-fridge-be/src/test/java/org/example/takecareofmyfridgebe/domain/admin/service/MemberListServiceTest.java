package org.example.takecareofmyfridgebe.domain.admin.service;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import org.example.takecareofmyfridgebe.domain.admin.dto.MemberSummaryResponse;
import org.example.takecareofmyfridgebe.domain.admin.entity.AdminAuditLog;
import org.example.takecareofmyfridgebe.domain.admin.repository.AdminAuditLogRepository;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.member.repository.MemberRepository;
import org.example.takecareofmyfridgebe.global.response.PageResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyChar;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberListServiceTest {

    @Mock
    private MemberRepository memberRepository;
    @Mock
    private AdminAuditLogRepository auditLogRepository;
    @InjectMocks
    private MemberListService service;

    @Test
    void emailAndRoleFiltersUseOneBasedPageAndStableSort() {
        Member member = Member.builder()
                .email("cook@example.com")
                .passwordHash("hash")
                .nickname("요리왕")
                .build();
        ReflectionTestUtils.setField(member, "id", 7L);
        Page<Member> result = new PageImpl<>(List.of(member), PageRequest.of(1, 20), 21);
        when(memberRepository.findAll(anySpecification(), any(Pageable.class))).thenReturn(result);

        PageResponse<MemberSummaryResponse> response = service.getMembers(" COOK ", Member.Role.USER, 2, 20);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(memberRepository).findAll(anySpecification(), captor.capture());
        assertEquals(1, captor.getValue().getPageNumber());
        assertEquals(20, captor.getValue().getPageSize());
        assertEquals(Sort.Direction.DESC, captor.getValue().getSort().getOrderFor("id").getDirection());
        assertEquals(2, response.pageNumber());
        assertEquals(21, response.totalElements());
        assertEquals(7L, response.content().get(0).memberId());
        assertEquals("cook@example.com", response.content().get(0).email());
        assertNull(response.content().get(0).promotedAt());
        assertNull(response.content().get(0).promotedBy());

        CriteriaMocks criteria = applyCapturedFilter();
        verify(criteria.cb).like(criteria.lowerEmail, "%cook%", '\\');
        verify(criteria.cb).equal(criteria.rolePath, Member.Role.USER);
    }

    @Test
    void adminSummaryUsesLatestSuccessfulPromotion() {
        Member admin = Member.builder()
                .email("new-admin@example.com")
                .passwordHash("hash")
                .nickname("새관리자")
                .role(Member.Role.ADMIN)
                .build();
        ReflectionTestUtils.setField(admin, "id", 12L);
        Member actor = Member.builder()
                .email("actor@example.com")
                .passwordHash("hash")
                .nickname("작업자")
                .role(Member.Role.ADMIN)
                .build();
        AdminAuditLog promotion = AdminAuditLog.create(actor, AdminAuditLog.Action.ROLE_CHANGE,
                "Member", 12L, AdminAuditLog.Result.SUCCESS);
        LocalDateTime promotedAt = LocalDateTime.of(2026, 10, 17, 10, 30);
        ReflectionTestUtils.setField(promotion, "createdAt", promotedAt);
        when(memberRepository.findAll(anySpecification(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(admin)));
        when(auditLogRepository.findByActionAndResultAndTargetTypeAndTargetIdInOrderByCreatedAtDescIdDesc(
                eq(AdminAuditLog.Action.ROLE_CHANGE), eq(AdminAuditLog.Result.SUCCESS),
                eq("Member"), eq(Set.of(12L)))).thenReturn(List.of(promotion));

        var response = service.getMembers(null, null, 1, 20);

        assertEquals(promotedAt, response.content().get(0).promotedAt());
        assertEquals("actor@example.com", response.content().get(0).promotedBy());
    }

    @Test
    void noFiltersAddNoConditions() {
        when(memberRepository.findAll(anySpecification(), any(Pageable.class))).thenReturn(Page.empty());

        service.getMembers(null, null, 1, 20);

        CriteriaMocks criteria = applyCapturedFilter();
        verify(criteria.cb, never()).like(any(), anyString(), anyChar());
        verify(criteria.cb, never()).equal(any(), any(Object.class));
    }

    @Test
    void blankEmailUsesOnlyRoleFilter() {
        when(memberRepository.findAll(anySpecification(), any(Pageable.class))).thenReturn(Page.empty());

        service.getMembers("  ", Member.Role.ADMIN, 1, 20);

        CriteriaMocks criteria = applyCapturedFilter();
        verify(criteria.cb, never()).like(any(), anyString(), anyChar());
        verify(criteria.cb).equal(criteria.rolePath, Member.Role.ADMIN);
    }

    @Test
    void emailOnlyUsesEscapedPartialEmailFilter() {
        when(memberRepository.findAll(anySpecification(), any(Pageable.class))).thenReturn(Page.empty());

        service.getMembers("a_b%", null, 1, 20);

        CriteriaMocks criteria = applyCapturedFilter();
        verify(criteria.cb).like(criteria.lowerEmail, "%a\\_b\\%%", '\\');
        verify(criteria.cb, never()).equal(any(), any(Object.class));
    }

    @SuppressWarnings("unchecked")
    private static Specification<Member> anySpecification() {
        return any(Specification.class);
    }

    @SuppressWarnings("unchecked")
    private CriteriaMocks applyCapturedFilter() {
        ArgumentCaptor<Specification<Member>> captor = ArgumentCaptor.forClass(Specification.class);
        verify(memberRepository).findAll(captor.capture(), any(Pageable.class));
        CriteriaMocks criteria = new CriteriaMocks();
        captor.getValue().toPredicate(criteria.root, criteria.query, criteria.cb);
        return criteria;
    }

    @SuppressWarnings("unchecked")
    private static class CriteriaMocks {
        final Root<Member> root = mock(Root.class);
        final CriteriaQuery<?> query = mock(CriteriaQuery.class);
        final CriteriaBuilder cb = mock(CriteriaBuilder.class);
        final Path<Object> emailPath = mock(Path.class);
        final Path<Object> rolePath = mock(Path.class);
        final Expression<String> lowerEmail = mock(Expression.class);

        CriteriaMocks() {
            lenient().when(root.get("email")).thenReturn(emailPath);
            lenient().when(root.get("role")).thenReturn(rolePath);
            lenient().when(cb.lower(any())).thenReturn(lowerEmail);
        }
    }
}
