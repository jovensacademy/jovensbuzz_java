package com.jovensbuzz.admin.controller;

import com.jovensbuzz.admin.dto.AdminUserDto;
import com.jovensbuzz.admin.dto.AdminUserRequests;
import com.jovensbuzz.admin.entity.AdminPermission;
import com.jovensbuzz.admin.service.AdminUserManagementService;
import com.jovensbuzz.common.response.ApiResponse;
import com.jovensbuzz.common.util.TimeZones;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Owner-only user management. Access is restricted to PERM_USERS_MANAGE in SecurityConfig.
 */
@RestController
@RequestMapping("/api/v1/admin/admin-users")
@Tag(name = "Admin User Management", description = "Owner-only management of dashboard accounts, roles and permissions")
@SecurityRequirement(name = "bearerAuth")
public class AdminUserManagementController {

    private final AdminUserManagementService service;

    public AdminUserManagementController(AdminUserManagementService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List admin users", description = "All accounts that have not been deleted")
    public ResponseEntity<ApiResponse<List<AdminUserDto>>> list() {
        return ResponseEntity.ok(ApiResponse.success(service.list()));
    }

    @GetMapping("/options")
    @Operation(summary = "Roles, assignable permissions and time zones for the user form")
    public ResponseEntity<ApiResponse<Map<String, Object>>> options() {
        List<Map<String, String>> permissions = Arrays.stream(AdminPermission.values())
                .filter(AdminPermission::isAssignableToManager)
                .map(p -> Map.of("id", p.name(), "label", p.getDescription()))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "roles", List.of("OWNER", "MANAGER", "USER"),
                "permissions", permissions,
                "managerDefaults", AdminPermission.MANAGER_DEFAULTS.stream().map(Enum::name).sorted().toList(),
                "userPermissions", AdminPermission.READ_ONLY.stream().map(Enum::name).sorted().toList(),
                "userDefaults", AdminPermission.USER_DEFAULTS.stream().map(Enum::name).sorted().toList(),
                "timezones", TimeZones.OPTIONS)));
    }

    @PostMapping
    @Operation(summary = "Create an admin user", description = "The owner sets the initial password; it is stored only as a BCrypt hash and never returned")
    public ResponseEntity<ApiResponse<AdminUserDto>> create(@Valid @RequestBody AdminUserRequests.Create request,
                                                            @AuthenticationPrincipal UserDetails actor) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Admin user created", service.create(request, actor.getUsername())));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update details, role, permissions and time zone")
    public ResponseEntity<ApiResponse<AdminUserDto>> update(@PathVariable Long id, @Valid @RequestBody AdminUserRequests.Update request,
                                                            @AuthenticationPrincipal UserDetails actor) {
        return ResponseEntity.ok(ApiResponse.success("Admin user updated", service.update(id, request, actor.getUsername())));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Enable or disable an account", description = "Disabling revokes all of the account's sessions")
    public ResponseEntity<ApiResponse<AdminUserDto>> setStatus(@PathVariable Long id, @Valid @RequestBody AdminUserRequests.Status request,
                                                               @AuthenticationPrincipal UserDetails actor) {
        AdminUserDto dto = service.setEnabled(id, request.enabled(), actor.getUsername());
        return ResponseEntity.ok(ApiResponse.success(request.enabled() ? "Account enabled" : "Account disabled; sessions revoked", dto));
    }

    @PostMapping("/{id}/reset-password")
    @Operation(summary = "Set a new password", description = "Owner-only; revokes all of the account's sessions; audited; never returned")
    public ResponseEntity<ApiResponse<AdminUserDto>> resetPassword(@PathVariable Long id, @Valid @RequestBody AdminUserRequests.PasswordReset request,
                                                                   @AuthenticationPrincipal UserDetails actor) {
        return ResponseEntity.ok(ApiResponse.success("New password set; all sessions signed out",
                service.resetPassword(id, request.newPassword(), actor.getUsername())));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an admin user", description = "Soft delete: revokes sessions and keeps audit history")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id, @AuthenticationPrincipal UserDetails actor) {
        service.delete(id, actor.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Admin user deleted", null));
    }
}
