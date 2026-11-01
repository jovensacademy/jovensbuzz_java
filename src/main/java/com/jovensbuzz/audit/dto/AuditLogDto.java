package com.jovensbuzz.audit.dto;

import com.jovensbuzz.audit.entity.AuditLog;

import java.time.Instant;

public class AuditLogDto {

    private Long id;
    private String adminUsername;
    private String action;
    private String entityType;
    private String entityId;
    private String safeMetadata;
    private Instant createdAt;

    public AuditLogDto() {}

    public static AuditLogDto fromEntity(AuditLog log) {
        AuditLogDto dto = new AuditLogDto();
        dto.setId(log.getId());
        dto.setAdminUsername(log.getAdminUser() != null ? log.getAdminUser().getUsername() : "SYSTEM");
        dto.setAction(log.getAction());
        dto.setEntityType(log.getEntityType());
        dto.setEntityId(log.getEntityId());
        dto.setSafeMetadata(log.getSafeMetadata());
        dto.setCreatedAt(log.getCreatedAt());
        return dto;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAdminUsername() {
        return adminUsername;
    }

    public void setAdminUsername(String adminUsername) {
        this.adminUsername = adminUsername;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public String getSafeMetadata() {
        return safeMetadata;
    }

    public void setSafeMetadata(String safeMetadata) {
        this.safeMetadata = safeMetadata;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
