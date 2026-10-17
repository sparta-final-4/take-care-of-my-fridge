package org.example.takecareofmyfridgebe.domain.admin.repository;

import org.example.takecareofmyfridgebe.domain.admin.entity.AdminAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {
}
