package com.jovensbuzz.auth.dto;

import com.jovensbuzz.admin.entity.AdminUser;
import java.time.Instant;

public class AdminProfileDto {
    private Long id;
    private String username;
    private String displayName;
    private String email;
    private String role;
    private Instant createdAt;
    private Instant lastLoginAt;
    private boolean mustChangePassword;
    private Instant passwordChangedAt;

    public AdminProfileDto() {}

    public static AdminProfileDto fromEntity(AdminUser admin) {
        AdminProfileDto dto = new AdminProfileDto();
        dto.setId(admin.getId());
        dto.setUsername(admin.getUsername());
        dto.setDisplayName(admin.getDisplayName());
        dto.setEmail(admin.getEmail());
        dto.setRole(admin.getRole());
        dto.setCreatedAt(admin.getCreatedAt());
        dto.setLastLoginAt(admin.getLastLoginAt());
        dto.setMustChangePassword(admin.isMustChangePassword());
        dto.setPasswordChangedAt(admin.getPasswordChangedAt());
        return dto;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(Instant lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public void setMustChangePassword(boolean mustChangePassword) {
        this.mustChangePassword = mustChangePassword;
    }

    public Instant getPasswordChangedAt() {
        return passwordChangedAt;
    }

    public void setPasswordChangedAt(Instant passwordChangedAt) {
        this.passwordChangedAt = passwordChangedAt;
    }
}
