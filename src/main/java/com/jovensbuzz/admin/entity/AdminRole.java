package com.jovensbuzz.admin.entity;

import java.util.Locale;

/**
 * Dashboard roles. OWNER (Super Admin) holds every permission; MANAGER holds only the permissions an owner
 * assigned; USER is read-only: at most the view/export permissions in {@link AdminPermission#READ_ONLY}.
 * Stored in admin_users.role as the Spring authority name (ROLE_OWNER / ROLE_MANAGER / ROLE_USER).
 */
public enum AdminRole {
    OWNER,
    MANAGER,
    USER;

    /** Permissions an owner may grant to this role (none to pick for an owner: it has all of them). */
    public java.util.Set<AdminPermission> grantable() {
        return switch (this) {
            case OWNER -> java.util.EnumSet.noneOf(AdminPermission.class);
            case MANAGER -> AdminPermission.assignable();
            case USER -> java.util.EnumSet.copyOf(AdminPermission.READ_ONLY);
        };
    }

    /** What a new account of this role receives when the owner does not choose. */
    public java.util.Set<AdminPermission> defaults() {
        return switch (this) {
            case OWNER -> java.util.EnumSet.noneOf(AdminPermission.class);
            case MANAGER -> java.util.EnumSet.copyOf(AdminPermission.MANAGER_DEFAULTS);
            case USER -> java.util.EnumSet.copyOf(AdminPermission.USER_DEFAULTS);
        };
    }

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
