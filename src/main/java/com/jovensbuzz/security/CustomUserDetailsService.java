package com.jovensbuzz.security;

import com.jovensbuzz.admin.entity.AdminUser;
import com.jovensbuzz.admin.repository.AdminUserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final AdminUserRepository adminUserRepository;

    public CustomUserDetailsService(AdminUserRepository adminUserRepository) {
        this.adminUserRepository = adminUserRepository;
    }

    // Not @Transactional: a "user not found" exception here must not mark the caller's
    // login transaction rollback-only (which would discard failed-attempt counters).
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AdminUser admin = adminUserRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return toUserDetails(admin);
    }

    /**
     * Resolves the principal for a verified JWT. Returns null when the account is disabled,
     * locked, or the token was revoked by a later logout/password change.
     */
    @Transactional(readOnly = true)
    public UserDetails loadForToken(String username, int tokenVersion) {
        return adminUserRepository.findByUsername(username)
                .filter(AdminUser::isEnabled)
                .filter(admin -> admin.getTokenVersion() == tokenVersion)
                .map(this::toUserDetails)
                .orElse(null);
    }

    private UserDetails toUserDetails(AdminUser admin) {
        boolean accountNonLocked = admin.getLockedUntil() == null || admin.getLockedUntil().isBefore(Instant.now());
        return new User(
                admin.getUsername(),
                admin.getPasswordHash(),
                admin.isEnabled(),
                true,
                true,
                accountNonLocked,
                Collections.singletonList(new SimpleGrantedAuthority(admin.getRole()))
        );
    }
}
