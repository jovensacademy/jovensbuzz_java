package com.jovensbuzz.admin.entity;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

/**
 * Fine-grained dashboard permissions, enforced on the API (see SecurityConfig) and mirrored in the UI.
 * Granted to Spring Security as "PERM_&lt;NAME&gt;" authorities.
 */
public enum AdminPermission {
    ENQUIRIES_VIEW("View enquiries"),
    ENQUIRIES_MANAGE("Update status, notes, assignment and sync of enquiries"),
    ENQUIRIES_IMPORT("Import enquiries from Excel"),
    ENQUIRIES_EXPORT("Export enquiries to Excel/CSV"),
    /** Not a manager default: an owner must grant it explicitly. */
    ENQUIRIES_DELETE("Delete enquiries (moves them to Deleted enquiries)"),
    ANALYTICS_VIEW("View the overview dashboard and analytics"),
    CONTENT_MANAGE("Manage website content, services and FAQs"),
    SETTINGS_VIEW("View integration settings and retry failed syncs"),
    AUDIT_VIEW("View the audit log"),
    /** Owner only: never assignable to a manager. */
    USERS_MANAGE("Manage admin users, roles and permissions");

    /** The only permissions a read-only USER can hold: nothing that changes data. */
    public static final Set<AdminPermission> READ_ONLY = EnumSet.of(ENQUIRIES_VIEW, ENQUIRIES_EXPORT, ANALYTICS_VIEW);

    /** Permissions a new user (read-only role) receives unless the owner chooses otherwise. */
    public static final Set<AdminPermission> USER_DEFAULTS = EnumSet.of(ENQUIRIES_VIEW, ANALYTICS_VIEW);

    /** Permissions a new manager receives unless the owner chooses otherwise. */
    public static final Set<AdminPermission> MANAGER_DEFAULTS = EnumSet.of(
            ENQUIRIES_VIEW, ENQUIRIES_MANAGE, ENQUIRIES_IMPORT, ENQUIRIES_EXPORT, ANALYTICS_VIEW);

    private final String description;

    AdminPermission(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public String authority() {
        return "PERM_" + name();
    }

    public boolean isAssignableToManager() {
        return this != USERS_MANAGE;
    }

    public static Set<AdminPermission> assignable() {
        EnumSet<AdminPermission> set = EnumSet.allOf(AdminPermission.class);
        set.remove(USERS_MANAGE);
        return set;
    }

    public static Set<AdminPermission> all() {
        return EnumSet.allOf(AdminPermission.class);
    }

    static Set<AdminPermission> parse(String csv) {
        EnumSet<AdminPermission> set = EnumSet.noneOf(AdminPermission.class);
        if (csv == null || csv.isBlank()) return set;
        Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .forEach(name -> {
                    try {
                        set.add(AdminPermission.valueOf(name));
                    } catch (IllegalArgumentException ignored) {
                        // Unknown names (e.g. from a newer version) are ignored rather than granted
                    }
                });
        return set;
    }
}
