package org.example.takecareofmyfridgebe.domain.admin.service;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.admin.dto.MemberSummaryResponse;
import org.example.takecareofmyfridgebe.domain.admin.entity.AdminAuditLog;
import org.example.takecareofmyfridgebe.domain.admin.repository.AdminAuditLogRepository;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.member.repository.MemberRepository;
import org.example.takecareofmyfridgebe.global.response.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberListService {

    private final MemberRepository memberRepository;
    private final AdminAuditLogRepository auditLogRepository;

    public PageResponse<MemberSummaryResponse> getMembers(String keyword, Member.Role role, int page, int size) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "id"));
        String email = keyword == null || keyword.isBlank() ? null : keyword.strip();
        Page<Member> members;
        if (email == null && role == null) {
            members = memberRepository.findAll(pageable);
        } else if (email == null) {
            members = memberRepository.findByRole(role, pageable);
        } else if (role == null) {
            members = memberRepository.findByEmailContainingIgnoreCase(email, pageable);
        } else {
            members = memberRepository.findByEmailContainingIgnoreCaseAndRole(email, role, pageable);
        }
        Set<Long> adminIds = members.getContent().stream()
                .filter(member -> member.getRole() == Member.Role.ADMIN)
                .map(Member::getId).collect(Collectors.toSet());
        Map<Long, AdminAuditLog> promotions = new HashMap<>();
        if (!adminIds.isEmpty()) {
            auditLogRepository.findByActionAndResultAndTargetTypeAndTargetIdInOrderByCreatedAtDescIdDesc(
                    AdminAuditLog.Action.ROLE_CHANGE, AdminAuditLog.Result.SUCCESS, "Member", adminIds)
                    .forEach(log -> promotions.putIfAbsent(log.getTargetId(), log));
        }
        return PageResponse.of(members.map(member ->
                MemberSummaryResponse.from(member, promotions.get(member.getId()))));
    }
}
