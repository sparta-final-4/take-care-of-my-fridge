package org.example.takecareofmyfridgebe.domain.admin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.example.takecareofmyfridgebe.domain.admin.entity.AdminAuditLog;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public record AuditLogSearchRequest(
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        AdminAuditLog.Action action,
        AdminAuditLog.Result result,
        @Size(max = 50, message = "대상 종류는 50자 이하여야 합니다.") String targetType,
        @Min(value = 1, message = "대상 ID는 1 이상이어야 합니다.") Long targetId,
        @Min(value = 1, message = "페이지는 1 이상이어야 합니다.") Integer page,
        @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.") Integer size
) {
    public AuditLogSearchRequest {
        page = page == null ? 1 : page;
        size = size == null ? 20 : size;
    }
}
