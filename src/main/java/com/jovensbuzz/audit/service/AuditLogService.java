package com.jovensbuzz.audit.service;

import com.jovensbuzz.admin.entity.AdminUser;
import com.jovensbuzz.admin.repository.AdminUserRepository;
import com.jovensbuzz.audit.dto.AuditLogDto;
import com.jovensbuzz.audit.entity.AuditLog;
import com.jovensbuzz.audit.repository.AuditLogRepository;
import com.jovensbuzz.common.response.PagedResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final AdminUserRepository adminUserRepository;

    public AuditLogService(AuditLogRepository auditLogRepository, AdminUserRepository adminUserRepository) {
        this.auditLogRepository = auditLogRepository;
        this.adminUserRepository = adminUserRepository;
    }

    @Transactional
    public void logAction(String username, String action, String entityType, String entityId, String safeMetadata) {
        AdminUser admin = null;
        if (username != null) {
            admin = adminUserRepository.findByUsername(username).orElse(null);
        }
        String metadata = safeMetadata != null && safeMetadata.length() > 1000 ? safeMetadata.substring(0, 1000) : safeMetadata;
        AuditLog auditLog = new AuditLog(admin, action, entityType, entityId, metadata);
        auditLogRepository.save(auditLog);
    }

    @Transactional(readOnly = true)
    public PagedResponse<AuditLogDto> getAuditLogs(Pageable pageable) {
        Page<AuditLogDto> page = auditLogRepository.findAllByOrderByCreatedAtDesc(pageable).map(AuditLogDto::fromEntity);
        return PagedResponse.fromPage(page);
    }
}
