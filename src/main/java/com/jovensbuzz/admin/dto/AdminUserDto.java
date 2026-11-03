package com.jovensbuzz.admin.dto;

import com.jovensbuzz.admin.entity.AdminUser;

import java.time.Instant;
import java.util.List;

/**
 * Administrator as shown in User Management. Never contains the password hash or token version.
 */
public record AdminUserDto(
        Long id,
        String username,
        String displayName,
        String email,
        String role,
        List<String> permissions,
        String timezone,
        boolean enabled,
        boolean locked,
        // When the password was last set by an owner; never the password itself
        Instant passwordChangedAt,
        Instant lastLoginAt,
        Instant createdAt,
        Instant updatedAt) {

    public static AdminUserDto fromEntity(AdminUser admin) {
        boolean locked = admin.getLockedUntil() != null && admin.getLockedUntil().isAfter(Instant.now());
        return new AdminUserDto(
                admin.getId(),
                admin.getUsername(),
                admin.getDisplayName(),
                admin.getEmail(),
                admin.getAdminRole().name(),
                admin.getEffectivePermissions().stream().map(Enum::name).sorted().toList(),
                admin.getTimezone(),
                admin.isEnabled(),
                locked,
                admin.getPasswordChangedAt(),
                admin.getLastLoginAt(),
                admin.getCreatedAt(),
                admin.getUpdatedAt());
    }
}
