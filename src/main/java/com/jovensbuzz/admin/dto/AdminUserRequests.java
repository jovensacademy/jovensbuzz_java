package com.jovensbuzz.admin.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Request bodies for /api/v1/admin/admin-users. */
public final class AdminUserRequests {

    private AdminUserRequests() {}

    public record Create(
            @NotBlank(message = "Username is required")
            @Size(min = 3, max = 64, message = "Username must be 3–64 characters")
            @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "Username may contain letters, digits, dots, hyphens and underscores")
            String username,
            @NotBlank(message = "Display name is required") @Size(max = 128) String displayName,
            @NotBlank(message = "Email is required") @Email(message = "Please provide a valid email address") @Size(max = 128) String email,
            @NotBlank(message = "Role is required") String role,
            List<String> permissions,
            @NotBlank(message = "Time zone is required") @Size(max = 64) String timezone,
            // Set by the owner and shown to them once; stored only as a BCrypt hash ("temporaryPassword" accepted for older clients)
            @JsonAlias("temporaryPassword") @NotBlank(message = "An initial password is required") @Size(max = 128) String initialPassword) {}

    public record Update(
            @NotBlank(message = "Display name is required") @Size(max = 128) String displayName,
            @NotBlank(message = "Email is required") @Email(message = "Please provide a valid email address") @Size(max = 128) String email,
            @NotBlank(message = "Role is required") String role,
            List<String> permissions,
            @NotBlank(message = "Time zone is required") @Size(max = 64) String timezone) {}

    public record Status(@NotNull(message = "enabled is required") Boolean enabled) {}

    public record PasswordReset(
            @JsonAlias("temporaryPassword") @NotBlank(message = "A new password is required") @Size(max = 128) String newPassword) {}
}
