package com.jovensbuzz.auth.controller;

import com.jovensbuzz.auth.dto.AdminProfileDto;
import com.jovensbuzz.auth.dto.LoginRequest;
import com.jovensbuzz.auth.dto.LoginResponse;
import com.jovensbuzz.auth.dto.UpdateProfileRequest;
import com.jovensbuzz.auth.service.AuthService;
import com.jovensbuzz.common.response.ApiResponse;
import com.jovensbuzz.common.util.TimeZones;
import java.util.List;
import java.util.Map;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * Token-based admin authentication. The API never sets cookies: the Next.js server stores
 * the returned token in its own HttpOnly/SameSite=Strict cookie and forwards it as a header.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Admin authentication, session, and profile management")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(summary = "Admin login", description = "Authenticates an administrator and issues a short-lived JWT")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Login successful", authService.login(request)));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token", description = "Exchanges a still-valid token for a new one with a fresh expiry")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success(authService.refresh(userDetails.getUsername())));
    }

    @PostMapping("/logout")
    @Operation(summary = "Admin logout", description = "Revokes all tokens issued to the calling administrator")
    public ResponseEntity<ApiResponse<Void>> logout(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails != null) {
            authService.logout(userDetails.getUsername());
        }
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully", null));
    }

    @GetMapping("/me")
    @Operation(summary = "Current admin profile", description = "Returns details of the authenticated administrator")
    public ResponseEntity<ApiResponse<AdminProfileDto>> getProfile(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success(authService.getCurrentUserProfile(userDetails.getUsername())));
    }

    @GetMapping("/timezones")
    @Operation(summary = "Time zones for profile settings", description = "IANA zones offered in the dashboard")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> timezones() {
        return ResponseEntity.ok(ApiResponse.success(TimeZones.OPTIONS));
    }

    @PutMapping("/me")
    @Operation(summary = "Update admin profile", description = "Updates the display name and email of the authenticated administrator")
    public ResponseEntity<ApiResponse<AdminProfileDto>> updateProfile(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Profile updated",
                authService.updateProfile(userDetails.getUsername(), request)));
    }

}
