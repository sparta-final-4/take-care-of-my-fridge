package org.example.takecareofmyfridgebe.domain.admin.repository;

import org.example.takecareofmyfridgebe.domain.admin.entity.AdminAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.Collection;
import java.util.List;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {
    @EntityGraph(attributePaths = "member")
    List<AdminAuditLog> findByActionAndResultAndTargetTypeAndTargetIdInOrderByCreatedAtDescIdDesc(
            AdminAuditLog.Action action, AdminAuditLog.Result result, String targetType,
            Collection<Long> targetIds);
}
