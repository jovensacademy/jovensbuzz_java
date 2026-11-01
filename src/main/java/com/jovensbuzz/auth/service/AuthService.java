package com.jovensbuzz.auth.service;

import com.jovensbuzz.admin.entity.AdminUser;
import com.jovensbuzz.admin.repository.AdminUserRepository;
import com.jovensbuzz.audit.service.AuditLogService;
import com.jovensbuzz.auth.dto.AdminProfileDto;
import com.jovensbuzz.auth.dto.ChangePasswordRequest;
import com.jovensbuzz.auth.dto.LoginRequest;
import com.jovensbuzz.auth.dto.LoginResponse;
import com.jovensbuzz.auth.dto.UpdateProfileRequest;
import com.jovensbuzz.common.exception.AccountLockedException;
import com.jovensbuzz.common.exception.BadRequestException;
import com.jovensbuzz.common.exception.UnauthorizedException;
import com.jovensbuzz.common.validation.PasswordPolicy;
import com.jovensbuzz.common.validation.InputSanitizer;
import com.jovensbuzz.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final int maxFailedAttempts;
    private final Duration lockoutDuration;

    public AuthService(
            AuthenticationManager authenticationManager,
            JwtTokenProvider tokenProvider,
            AdminUserRepository adminUserRepository,
            PasswordEncoder passwordEncoder,
            AuditLogService auditLogService,
            @Value("${app.security.login.max-failed-attempts:5}") int maxFailedAttempts,
            @Value("${app.security.login.lockout-minutes:15}") long lockoutMinutes) {
        this.authenticationManager = authenticationManager;
        this.tokenProvider = tokenProvider;
        this.adminUserRepository = adminUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
        this.maxFailedAttempts = maxFailedAttempts;
        this.lockoutDuration = Duration.ofMinutes(lockoutMinutes);
    }

    // Failed-attempt counters must survive the exception thrown back to the caller
    @Transactional(noRollbackFor = {AuthenticationException.class, AccountLockedException.class})
    public LoginResponse login(LoginRequest request) {
        String username = request.getUsername().trim();
        AdminUser admin = adminUserRepository.findByUsername(username).orElse(null);
        Instant now = Instant.now();

        if (admin != null && admin.getLockedUntil() != null && admin.getLockedUntil().isAfter(now)) {
            throw new AccountLockedException("Too many failed sign-in attempts. Try again in a few minutes.");
        }

        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, request.getPassword()));
        } catch (AuthenticationException ex) {
            if (admin != null) {
                registerFailedAttempt(admin, now);
            }
            throw ex;
        }

        // authenticate() succeeded, so the account exists
        admin.setFailedLoginAttempts(0);
        admin.setLockedUntil(null);
        admin.setLastLoginAt(now);
        adminUserRepository.save(admin);
        auditLogService.logAction(admin.getUsername(), "LOGIN", "ADMIN_USER", String.valueOf(admin.getId()), "Successful sign-in");

        return buildLoginResponse(admin);
    }

    private void registerFailedAttempt(AdminUser admin, Instant now) {
        int attempts = admin.getFailedLoginAttempts() + 1;
        admin.setFailedLoginAttempts(attempts);
        if (attempts >= maxFailedAttempts) {
            admin.setLockedUntil(now.plus(lockoutDuration));
            admin.setFailedLoginAttempts(0);
            log.warn("Admin account '{}' locked for {} after repeated failed sign-ins", admin.getUsername(), lockoutDuration);
            auditLogService.logAction(null, "ACCOUNT_LOCKED", "ADMIN_USER", String.valueOf(admin.getId()),
                    "Locked after " + attempts + " failed sign-in attempts");
        }
        adminUserRepository.save(admin);
    }

    @Transactional(readOnly = true)
    public LoginResponse refresh(String username) {
        return buildLoginResponse(requireAdmin(username));
    }

    /**
     * Revokes every token issued to this admin so far.
     */
    @Transactional
    public void logout(String username) {
        adminUserRepository.findByUsername(username).ifPresent(admin -> {
            admin.setTokenVersion(admin.getTokenVersion() + 1);
            adminUserRepository.save(admin);
            auditLogService.logAction(username, "LOGOUT", "ADMIN_USER", String.valueOf(admin.getId()), "Signed out; sessions revoked");
        });
    }

    @Transactional(readOnly = true)
    public AdminProfileDto getCurrentUserProfile(String username) {
        return AdminProfileDto.fromEntity(requireAdmin(username));
    }

    @Transactional
    public AdminProfileDto updateProfile(String username, UpdateProfileRequest request) {
        AdminUser admin = requireAdmin(username);
        String email = request.getEmail().trim().toLowerCase();
        adminUserRepository.findByEmail(email)
                .filter(other -> !other.getId().equals(admin.getId()))
                .ifPresent(other -> { throw new BadRequestException("That email address is already used by another administrator"); });

        admin.setDisplayName(InputSanitizer.plainText(request.getDisplayName()));
        admin.setEmail(email);
        adminUserRepository.save(admin);
        auditLogService.logAction(username, "UPDATE_PROFILE", "ADMIN_USER", String.valueOf(admin.getId()), "Updated display name/email");
        return AdminProfileDto.fromEntity(admin);
    }

    /**
     * Changes the password and revokes all existing sessions. Returns a fresh token
     * so the caller's current session continues seamlessly.
     */
    @Transactional
    public LoginResponse changePassword(String username, ChangePasswordRequest request) {
        AdminUser admin = requireAdmin(username);

        if (!passwordEncoder.matches(request.getCurrentPassword(), admin.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect");
        }
        if (passwordEncoder.matches(request.getNewPassword(), admin.getPasswordHash())) {
            throw new BadRequestException("New password must be different from the current password");
        }
        List<String> problems = PasswordPolicy.validate(request.getNewPassword(), admin.getUsername());
        if (!problems.isEmpty()) {
            throw new BadRequestException("Password does not meet the policy: " + String.join("; ", problems));
        }

        admin.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        admin.setPasswordChangedAt(Instant.now());
        admin.setMustChangePassword(false);
        admin.setTokenVersion(admin.getTokenVersion() + 1);
        adminUserRepository.save(admin);
        auditLogService.logAction(username, "CHANGE_PASSWORD", "ADMIN_USER", String.valueOf(admin.getId()), "Password changed; other sessions revoked");

        return buildLoginResponse(admin);
    }

    private AdminUser requireAdmin(String username) {
        return adminUserRepository.findByUsername(username)
                .filter(AdminUser::isEnabled)
                .orElseThrow(() -> new UnauthorizedException("Session is no longer valid"));
    }

    private LoginResponse buildLoginResponse(AdminUser admin) {
        JwtTokenProvider.IssuedToken issued = tokenProvider.generateToken(admin);
        LoginResponse response = new LoginResponse(
                issued.token(),
                admin.getUsername(),
                admin.getDisplayName(),
                admin.getEmail(),
                admin.getRole()
        );
        response.setExpiresAt(issued.expiresAt());
        response.setMustChangePassword(admin.isMustChangePassword());
        return response;
    }
}
