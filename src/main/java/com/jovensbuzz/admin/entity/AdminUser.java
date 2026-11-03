package com.jovensbuzz.admin.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "admin_users")
public class AdminUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    @Column(nullable = false, length = 128)
    private String email;

    @Column(nullable = false, length = 32)
    private String role = "ROLE_MANAGER";

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    // Incremented on logout / password change to revoke previously issued JWTs
    @Column(name = "token_version", nullable = false)
    private int tokenVersion = 0;

    // Legacy column (V3), no longer read: passwords are set by owners and never force a change.
    // Kept so existing rows and the schema stay untouched.
    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = false;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    // Comma-separated AdminPermission names; only meaningful for managers (owners hold all)
    @Column(length = 512)
    private String permissions;

    // IANA zone used to display dates for this user, e.g. Asia/Kolkata or America/Chicago
    @Column(nullable = false, length = 64)
    private String timezone = "America/Chicago";

    // Soft deletion: the row stays for audit history; the account can never sign in again
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by", length = 64)
    private String deletedBy;

    public AdminUser() {}

    public AdminUser(String username, String passwordHash, String displayName, String email, String role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.email = email;
        this.role = role != null ? role : "ROLE_MANAGER";
        this.enabled = true;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
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

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
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

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(Instant lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public int getTokenVersion() {
        return tokenVersion;
    }

    public void setTokenVersion(int tokenVersion) {
        this.tokenVersion = tokenVersion;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public void setMustChangePassword(boolean mustChangePassword) {
        this.mustChangePassword = mustChangePassword;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public void setFailedLoginAttempts(int failedLoginAttempts) {
        this.failedLoginAttempts = failedLoginAttempts;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(Instant lockedUntil) {
        this.lockedUntil = lockedUntil;
    }

    public Instant getPasswordChangedAt() {
        return passwordChangedAt;
    }

    public void setPasswordChangedAt(Instant passwordChangedAt) {
        this.passwordChangedAt = passwordChangedAt;
    }

    public String getPermissions() {
        return permissions;
    }

    public void setPermissions(String permissions) {
        this.permissions = permissions;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public String getDeletedBy() {
        return deletedBy;
    }

    public void setDeletedBy(String deletedBy) {
        this.deletedBy = deletedBy;
    }

    // ---------------------------------------------------------------- role helpers

    public AdminRole getAdminRole() {
        return AdminRole.from(role);
    }

    public boolean isOwner() {
        return getAdminRole() == AdminRole.OWNER;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /** Enabled, not deleted. (A temporary login lock does not make an account inactive.) */
    public boolean isActive() {
        return enabled && deletedAt == null;
    }

    /** Permissions stored for a manager (empty for owners, who hold all of them implicitly). */
    public Set<AdminPermission> getAssignedPermissions() {
        return AdminPermission.parse(permissions);
    }

    public void setAssignedPermissions(Set<AdminPermission> assigned) {
        this.permissions = assigned == null || assigned.isEmpty() ? null : assigned.stream()
                .filter(AdminPermission::isAssignableToManager)
                .map(Enum::name)
                .sorted()
                .collect(Collectors.joining(","));
    }

    /**
     * What this account may actually do: everything for an owner, the assigned subset for a manager, and the
     * assigned read-only subset for a user (capped here too, so a stored write permission can never apply).
     */
    public Set<AdminPermission> getEffectivePermissions() {
        AdminRole adminRole = getAdminRole();
        if (adminRole == AdminRole.OWNER) {
            return AdminPermission.all();
        }
        EnumSet<AdminPermission> set = EnumSet.noneOf(AdminPermission.class);
        set.addAll(getAssignedPermissions());
        set.retainAll(adminRole.grantable());
        return set;
    }
}
