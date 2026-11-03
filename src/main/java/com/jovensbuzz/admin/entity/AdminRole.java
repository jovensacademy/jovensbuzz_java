package com.jovensbuzz.admin.entity;

import java.util.Locale;

/**
 * Dashboard roles. OWNER holds every permission; MANAGER holds only the permissions an owner assigned.
 * Stored in admin_users.role as the Spring authority name (ROLE_OWNER / ROLE_MANAGER).
 */
public enum AdminRole {
    OWNER,
    MANAGER;

    public String authority() {
        return "ROLE_" + name();
    }

    /** Parses a stored or requested role; the pre-V7 ROLE_ADMIN value means OWNER. */
    public static AdminRole from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Role is required");
        }
        String normalised = value.trim().toUpperCase(Locale.ROOT).replaceFirst("^ROLE_", "");
        if (normalised.equals("ADMIN")) {
            return OWNER;
        }
        return AdminRole.valueOf(normalised);
    }
}
