package org.example.takecareofmyfridgebe.domain.admin.repository;

import org.example.takecareofmyfridgebe.domain.admin.entity.AdminAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collection;
import java.util.List;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long>, JpaSpecificationExecutor<AdminAuditLog> {
    // 목록 응답이 작업자 이메일을 쓰므로 회원을 함께 읽어 행마다 회원 조회가 나가지 않게 한다.
    @Override
    @EntityGraph(attributePaths = "member")
    Page<AdminAuditLog> findAll(Specification<AdminAuditLog> spec, Pageable pageable);

    @EntityGraph(attributePaths = "member")
    List<AdminAuditLog> findByActionAndResultAndTargetTypeAndTargetIdInOrderByCreatedAtDescIdDesc(
            AdminAuditLog.Action action, AdminAuditLog.Result result, String targetType,
            Collection<Long> targetIds);
}
