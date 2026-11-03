package com.jovensbuzz.security;

import com.jovensbuzz.admin.entity.AdminUser;
import com.jovensbuzz.admin.repository.AdminUserRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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
                .filter(a -> !a.isDeleted())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return toUserDetails(admin);
    }

    /**
     * Resolves the principal for a verified JWT. Returns null when the account is disabled, deleted,
     * locked, or the token was revoked by a later logout/password change/role or status change.
     * Authorities are read from the database on every request, so permission changes apply at once.
     */
    @Transactional(readOnly = true)
    public UserDetails loadForToken(String username, int tokenVersion) {
        return adminUserRepository.findByUsername(username)
                .filter(AdminUser::isActive)
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
                authoritiesOf(admin)
        );
    }

    /** ROLE_OWNER / ROLE_MANAGER plus one PERM_* authority per effective permission. */
    public static List<GrantedAuthority> authoritiesOf(AdminUser admin) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(admin.getAdminRole().authority()));
        admin.getEffectivePermissions().forEach(p -> authorities.add(new SimpleGrantedAuthority(p.authority())));
        return authorities;
    }
}
