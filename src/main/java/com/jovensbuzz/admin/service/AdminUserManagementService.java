package com.jovensbuzz.admin.service;

import com.jovensbuzz.admin.dto.AdminUserDto;
import com.jovensbuzz.admin.dto.AdminUserRequests;
import com.jovensbuzz.admin.entity.AdminPermission;
import com.jovensbuzz.admin.entity.AdminRole;
import com.jovensbuzz.admin.entity.AdminUser;
import com.jovensbuzz.admin.repository.AdminUserRepository;
import com.jovensbuzz.audit.service.AuditLogService;
import com.jovensbuzz.common.exception.BadRequestException;
import com.jovensbuzz.common.exception.ResourceNotFoundException;
import com.jovensbuzz.common.util.TimeZones;
import com.jovensbuzz.common.validation.InputSanitizer;
import com.jovensbuzz.common.validation.PasswordPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Owner-only administration of dashboard accounts (the API path is restricted to PERM_USERS_MANAGE).
 *
 * Safety rules, all enforced here inside a transaction:
 * - nobody can disable, delete or change the role of their own account;
 * - the last active owner can never be disabled, deleted or demoted (owner rows are locked while checking);
 * - disabling, deleting or setting a new password revokes every existing session (token version bump);
 * - deletion is soft: the row, its audit history, notes and assignments are preserved.
 */
@Service
public class AdminUserManagementService {

    private final AdminUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    public AdminUserManagementService(AdminUserRepository repository, PasswordEncoder passwordEncoder,
                                      AuditLogService auditLogService) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<AdminUserDto> list() {
        return repository.findByDeletedAtIsNullOrderByCreatedAtAsc().stream().map(AdminUserDto::fromEntity).toList();
    }

    @Transactional
    public AdminUserDto create(AdminUserRequests.Create request, String actor) {
        String username = request.username().trim();
        if (repository.existsByUsername(username)) {
            throw new BadRequestException("That username is already taken (usernames of deleted accounts stay reserved)");
        }
        String email = normaliseEmail(request.email());
        requireUnusedEmail(email, null);
        AdminRole role = parseRole(request.role());
        List<String> problems = PasswordPolicy.validate(request.initialPassword(), username);
        if (!problems.isEmpty()) {
            throw new BadRequestException("Password does not meet the policy: " + String.join("; ", problems));
        }

        AdminUser admin = new AdminUser(username, passwordEncoder.encode(request.initialPassword()),
                InputSanitizer.plainText(request.displayName()), email, role.authority());
        admin.setTimezone(TimeZones.requireRegion(request.timezone()));
        admin.setAssignedPermissions(role == AdminRole.MANAGER ? parsePermissions(request.permissions(), true) : null);
        admin.setPasswordChangedAt(Instant.now());
        repository.save(admin);
        auditLogService.logAction(actor, "CREATE_ADMIN_USER", "ADMIN_USER", String.valueOf(admin.getId()),
                "Created " + role.name() + " '" + username + "' (" + admin.getTimezone() + ")" + permissionNote(admin));
        return AdminUserDto.fromEntity(admin);
    }

    @Transactional
    public AdminUserDto update(Long id, AdminUserRequests.Update request, String actor) {
        AdminUser admin = requireExisting(id);
        AdminRole newRole = parseRole(request.role());
        AdminRole oldRole = admin.getAdminRole();
        boolean self = admin.getUsername().equals(actor);
        if (self && newRole != oldRole) {
            throw new BadRequestException("You cannot change the role of your own account");
        }
        if (oldRole == AdminRole.OWNER && newRole != AdminRole.OWNER) {
            requireAnotherActiveOwner(admin, "demote");
        }

        String email = normaliseEmail(request.email());
        requireUnusedEmail(email, admin.getId());
        admin.setDisplayName(InputSanitizer.plainText(request.displayName()));
        admin.setEmail(email);
        admin.setTimezone(TimeZones.requireRegion(request.timezone()));
        admin.setRole(newRole.authority());
        admin.setAssignedPermissions(newRole == AdminRole.MANAGER ? parsePermissions(request.permissions(), false) : null);
        repository.save(admin);

        String roleNote = newRole != oldRole ? "; role " + oldRole + " -> " + newRole : "";
        auditLogService.logAction(actor, "UPDATE_ADMIN_USER", "ADMIN_USER", String.valueOf(id),
                "Updated '" + admin.getUsername() + "'" + roleNote + permissionNote(admin) + "; time zone " + admin.getTimezone());
        return AdminUserDto.fromEntity(admin);
    }

    @Transactional
    public AdminUserDto setEnabled(Long id, boolean enabled, String actor) {
        AdminUser admin = requireExisting(id);
        if (admin.isEnabled() == enabled) {
            return AdminUserDto.fromEntity(admin);
        }
        if (!enabled) {
            if (admin.getUsername().equals(actor)) {
                throw new BadRequestException("You cannot disable your own account");
            }
            if (admin.isOwner()) {
                requireAnotherActiveOwner(admin, "disable");
            }
            admin.setTokenVersion(admin.getTokenVersion() + 1); // ends every active session immediately
        } else {
            admin.setFailedLoginAttempts(0);
            admin.setLockedUntil(null);
        }
        admin.setEnabled(enabled);
        repository.save(admin);
        auditLogService.logAction(actor, enabled ? "ENABLE_ADMIN_USER" : "DISABLE_ADMIN_USER", "ADMIN_USER", String.valueOf(id),
                (enabled ? "Enabled '" : "Disabled '") + admin.getUsername() + "'" + (enabled ? "" : "; sessions revoked"));
        return AdminUserDto.fromEntity(admin);
    }

    @Transactional
    public AdminUserDto resetPassword(Long id, String newPassword, String actor) {
        // Owners set every password, including their own (there is no self-service change)
        AdminUser admin = requireExisting(id);
        List<String> problems = PasswordPolicy.validate(newPassword, admin.getUsername());
        if (!problems.isEmpty()) {
            throw new BadRequestException("Password does not meet the policy: " + String.join("; ", problems));
        }
        admin.setPasswordHash(passwordEncoder.encode(newPassword));
        admin.setPasswordChangedAt(Instant.now());
        admin.setFailedLoginAttempts(0);
        admin.setLockedUntil(null);
        admin.setTokenVersion(admin.getTokenVersion() + 1);
        repository.save(admin);
        auditLogService.logAction(actor, "RESET_ADMIN_PASSWORD", "ADMIN_USER", String.valueOf(id),
                "New password set for '" + admin.getUsername() + "'" + (admin.getUsername().equals(actor) ? " (own account)" : "") + "; all sessions revoked");
        return AdminUserDto.fromEntity(admin);
    }

    @Transactional
    public void delete(Long id, String actor) {
        AdminUser admin = requireExisting(id);
        if (admin.getUsername().equals(actor)) {
            throw new BadRequestException("You cannot delete your own account");
        }
        if (admin.isOwner() && admin.isEnabled()) {
            requireAnotherActiveOwner(admin, "delete");
        }
        admin.setDeletedAt(Instant.now());
        admin.setDeletedBy(actor);
        admin.setEnabled(false);
        admin.setTokenVersion(admin.getTokenVersion() + 1);
        repository.save(admin);
        auditLogService.logAction(actor, "DELETE_ADMIN_USER", "ADMIN_USER", String.valueOf(id),
                "Deleted " + admin.getAdminRole() + " '" + admin.getUsername() + "' (soft delete; sessions revoked; history kept)");
    }

    // ---------------------------------------------------------------- helpers

    private AdminUser requireExisting(Long id) {
        return repository.findById(id)
                .filter(a -> !a.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Admin user", "id", id));
    }

    private void requireAnotherActiveOwner(AdminUser target, String action) {
        boolean another = repository.lockActiveOwners().stream().anyMatch(o -> !o.getId().equals(target.getId()));
        if (!another) {
            throw new BadRequestException("You cannot " + action + " the last active owner. Add or enable another owner first.");
        }
    }

    private void requireUnusedEmail(String email, Long exceptId) {
        repository.findFirstByEmailIgnoreCaseAndDeletedAtIsNull(email)
                .filter(other -> !other.getId().equals(exceptId))
                .ifPresent(other -> { throw new BadRequestException("That email address is already used by another administrator"); });
    }

    private static String normaliseEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static AdminRole parseRole(String role) {
        try {
            return AdminRole.from(role);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Role must be OWNER or MANAGER");
        }
    }

    /** Validates requested manager permissions; null on create means the manager defaults. */
    static Set<AdminPermission> parsePermissions(List<String> requested, boolean defaultsWhenNull) {
        if (requested == null) {
            return defaultsWhenNull ? EnumSet.copyOf(AdminPermission.MANAGER_DEFAULTS) : EnumSet.noneOf(AdminPermission.class);
        }
        EnumSet<AdminPermission> set = EnumSet.noneOf(AdminPermission.class);
        for (String name : requested) {
            AdminPermission permission;
            try {
                permission = AdminPermission.valueOf(name.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Unknown permission: " + name);
            }
            if (!permission.isAssignableToManager()) {
                throw new BadRequestException("Managing admin users is reserved for owners");
            }
            set.add(permission);
        }
        return set;
    }

    private static String permissionNote(AdminUser admin) {
        return admin.isOwner() ? "" : "; permissions " + admin.getAssignedPermissions();
    }
}
