package org.example.takecareofmyfridgebe.domain.admin.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.admin.dto.AuditLogResponse;
import org.example.takecareofmyfridgebe.domain.admin.entity.AdminAuditLog;
import org.example.takecareofmyfridgebe.domain.admin.repository.AdminAuditLogRepository;
import org.example.takecareofmyfridgebe.global.response.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAuditSearchService {

    private final AdminAuditLogRepository auditLogRepository;

    public PageResponse<AuditLogResponse> search(LocalDate from, LocalDate to,
            AdminAuditLog.Action action, AdminAuditLog.Result result, String targetType,
            Long targetId, int page, int size) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusDays(6) : from;
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("시작일은 종료일보다 늦을 수 없습니다.");
        }
        LocalDateTime startTime = start.atStartOfDay();
        LocalDateTime endExclusive = end.plusDays(1).atStartOfDay();
        String type = targetType == null || targetType.isBlank() ? null : targetType.strip();
        Specification<AdminAuditLog> filter = (root, query, cb) -> {
            List<Predicate> conditions = new ArrayList<>();
            conditions.add(cb.greaterThanOrEqualTo(root.<LocalDateTime>get("createdAt"), startTime));
            conditions.add(cb.lessThan(root.<LocalDateTime>get("createdAt"), endExclusive));
            if (action != null) {
                conditions.add(cb.equal(root.get("action"), action));
            }
            if (result == AdminAuditLog.Result.FAILED) {
                conditions.add(root.get("result").in(AdminAuditLog.Result.FAILED,
                        AdminAuditLog.Result.DENIED));
            } else if (result != null) {
                conditions.add(cb.equal(root.get("result"), result));
            }
            if (type != null) {
                conditions.add(cb.equal(root.get("targetType"), type));
            }
            if (targetId != null) {
                conditions.add(cb.equal(root.get("targetId"), targetId));
            }
            return cb.and(conditions.toArray(Predicate[]::new));
        };
        var sort = Sort.by(Sort.Direction.DESC, "createdAt", "id");
        return PageResponse.of(auditLogRepository.findAll(filter,
                PageRequest.of(page - 1, size, sort)).map(AuditLogResponse::from));
    }
}
