package com.jovensbuzz.auth.dto;

import com.jovensbuzz.admin.entity.AdminUser;
import java.time.Instant;
import java.util.List;

public class AdminProfileDto {
    private Long id;
    private String username;
    private String displayName;
    private String email;
    private String role;
    private Instant createdAt;
    private Instant lastLoginAt;
    private Instant passwordChangedAt;
    // OWNER or MANAGER, the effective permissions the UI should offer, and the display time zone
    private String adminRole;
    private List<String> permissions;
    private String timezone;

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
        dto.setPasswordChangedAt(admin.getPasswordChangedAt());
        dto.setAdminRole(admin.getAdminRole().name());
        dto.setPermissions(admin.getEffectivePermissions().stream().map(Enum::name).sorted().toList());
        dto.setTimezone(admin.getTimezone());
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


    public Instant getPasswordChangedAt() {
        return passwordChangedAt;
    }

    public void setPasswordChangedAt(Instant passwordChangedAt) {
        this.passwordChangedAt = passwordChangedAt;
    }

    public String getAdminRole() {
        return adminRole;
    }

    public void setAdminRole(String adminRole) {
        this.adminRole = adminRole;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<String> permissions) {
        this.permissions = permissions;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }
}
