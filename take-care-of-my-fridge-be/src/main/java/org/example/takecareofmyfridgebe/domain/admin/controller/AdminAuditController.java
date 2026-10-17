package org.example.takecareofmyfridgebe.domain.admin.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.admin.dto.AuditLogResponse;
import org.example.takecareofmyfridgebe.domain.admin.dto.AuditLogSearchRequest;
import org.example.takecareofmyfridgebe.domain.admin.service.AdminAuditSearchService;
import org.example.takecareofmyfridgebe.global.response.ApiResponse;
import org.example.takecareofmyfridgebe.global.response.PageResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/audit-logs")
@RequiredArgsConstructor
public class AdminAuditController {

    private final AdminAuditSearchService auditSearchService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AuditLogResponse>>> getAuditLogs(
            @Valid @ModelAttribute AuditLogSearchRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(auditSearchService.search(
                request.from(), request.to(), request.action(), request.result(),
                request.targetType(), request.targetId(), request.page(), request.size())));
    }
}
