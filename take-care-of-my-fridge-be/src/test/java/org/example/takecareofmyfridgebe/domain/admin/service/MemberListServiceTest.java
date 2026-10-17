package org.example.takecareofmyfridgebe.domain.admin.service;

import org.example.takecareofmyfridgebe.domain.admin.dto.MemberSummaryResponse;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberListServiceTest {

    @Mock
    private MemberRepository memberRepository;
    @InjectMocks
    private MemberListService service;

    @Test
    void emailAndRoleFiltersUseOneBasedPageAndStableSort() {
        Member member = Member.create("cook@example.com", "hash", "요리왕", Set.of());
        ReflectionTestUtils.setField(member, "id", 7L);
        Page<Member> result = new PageImpl<>(List.of(member), PageRequest.of(1, 20), 21);
        when(memberRepository.findByEmailContainingIgnoreCaseAndRole(
                eq("COOK"), eq(Member.Role.USER),
                any(Pageable.class))).thenReturn(result);

        PageResponse<MemberSummaryResponse> response = service.getMembers(" COOK ", Member.Role.USER, 2, 20);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(memberRepository).findByEmailContainingIgnoreCaseAndRole(
                eq("COOK"), eq(Member.Role.USER),
                captor.capture());
        assertEquals(1, captor.getValue().getPageNumber());
        assertEquals(20, captor.getValue().getPageSize());
        assertEquals(Sort.Direction.DESC, captor.getValue().getSort().getOrderFor("id").getDirection());
        assertEquals(2, response.pageNumber());
        assertEquals(21, response.totalElements());
        assertEquals(7L, response.content().get(0).memberId());
        assertEquals("cook@example.com", response.content().get(0).email());
        assertNull(response.content().get(0).promotedAt());
        assertNull(response.content().get(0).promotedBy());
    }

    @Test
    void noFiltersLoadsAllMembers() {
        when(memberRepository.findAll(any(Pageable.class))).thenReturn(Page.empty());

        service.getMembers(null, null, 1, 20);

        verify(memberRepository).findAll(any(Pageable.class));
    }

    @Test
    void blankEmailUsesOnlyRoleFilter() {
        when(memberRepository.findByRole(eq(Member.Role.ADMIN),
                any(Pageable.class))).thenReturn(Page.empty());

        service.getMembers("  ", Member.Role.ADMIN, 1, 20);

        verify(memberRepository).findByRole(eq(Member.Role.ADMIN),
                any(Pageable.class));
    }

    @Test
    void emailOnlyUsesPartialEmailFilter() {
        when(memberRepository.findByEmailContainingIgnoreCase(
                eq("cook"), any(Pageable.class))).thenReturn(Page.empty());

        service.getMembers("cook", null, 1, 20);

        verify(memberRepository).findByEmailContainingIgnoreCase(
                eq("cook"), any(Pageable.class));
    }
}
