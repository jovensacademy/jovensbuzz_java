package com.jovensbuzz;

import com.jovensbuzz.admin.entity.AdminUser;
import com.jovensbuzz.admin.repository.AdminUserRepository;
import com.jovensbuzz.audit.service.AuditLogService;
import com.jovensbuzz.auth.dto.ChangePasswordRequest;
import com.jovensbuzz.auth.dto.LoginRequest;
import com.jovensbuzz.auth.dto.LoginResponse;
import com.jovensbuzz.auth.service.AuthService;
import com.jovensbuzz.common.exception.AccountLockedException;
import com.jovensbuzz.common.exception.BadRequestException;
import com.jovensbuzz.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private AdminUserRepository adminUserRepository;
    @Mock
    private AuditLogService auditLogService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private AuthService authService;
    private AdminUser admin;

    @BeforeEach
    void setUp() {
        JwtTokenProvider tokenProvider = new JwtTokenProvider(JwtTokenProvider.DEFAULT_DEV_SECRET, 3_600_000);
        authService = new AuthService(authenticationManager, tokenProvider, adminUserRepository,
                passwordEncoder, auditLogService, 3, 15);

        admin = new AdminUser("jovens_buzz_2026", passwordEncoder.encode("Jovensbuzz@2026"), "Admin", "a@example.com", "ROLE_ADMIN");
        admin.setId(1L);
        admin.setMustChangePassword(true);
        when(adminUserRepository.findByUsername("jovens_buzz_2026")).thenReturn(Optional.of(admin));
    }

    @Test
    void login_success_resetsCountersAndReturnsShortLivedToken() {
        admin.setFailedLoginAttempts(2);

        LoginResponse response = authService.login(new LoginRequest("jovens_buzz_2026", "Jovensbuzz@2026"));

        assertNotNull(response.getToken());
        assertTrue(response.isMustChangePassword());
        assertTrue(response.getExpiresAt().isAfter(Instant.now()));
        assertEquals(0, admin.getFailedLoginAttempts());
        assertNotNull(admin.getLastLoginAt());
    }

    @Test
    void login_repeatedFailures_lockAccount() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));
        LoginRequest bad = new LoginRequest("jovens_buzz_2026", "wrong");

        for (int i = 0; i < 3; i++) {
            assertThrows(BadCredentialsException.class, () -> authService.login(bad));
        }
        assertNotNull(admin.getLockedUntil());
        assertTrue(admin.getLockedUntil().isAfter(Instant.now()));

        // Even the correct password is refused while locked
        assertThrows(AccountLockedException.class,
                () -> authService.login(new LoginRequest("jovens_buzz_2026", "Jovensbuzz@2026")));
    }

    @Test
    void logout_revokesExistingTokens() {
        int before = admin.getTokenVersion();
        authService.logout("jovens_buzz_2026");
        assertEquals(before + 1, admin.getTokenVersion());
    }

    @Test
    void changePassword_enforcesPolicy_andRevokesOldSessions() {
        assertThrows(BadRequestException.class, () -> authService.changePassword("jovens_buzz_2026",
                new ChangePasswordRequest("wrong-current", "Another#Strong2027")));
        assertThrows(BadRequestException.class, () -> authService.changePassword("jovens_buzz_2026",
                new ChangePasswordRequest("Jovensbuzz@2026", "weakpassword")));
        assertThrows(BadRequestException.class, () -> authService.changePassword("jovens_buzz_2026",
                new ChangePasswordRequest("Jovensbuzz@2026", "Jovensbuzz@2026")));

        int before = admin.getTokenVersion();
        LoginResponse response = authService.changePassword("jovens_buzz_2026",
                new ChangePasswordRequest("Jovensbuzz@2026", "Another#Strong2027"));

        assertTrue(passwordEncoder.matches("Another#Strong2027", admin.getPasswordHash()));
        assertFalse(admin.isMustChangePassword());
        assertEquals(before + 1, admin.getTokenVersion());
        assertFalse(response.isMustChangePassword());
        assertNotNull(response.getToken());
    }
}
