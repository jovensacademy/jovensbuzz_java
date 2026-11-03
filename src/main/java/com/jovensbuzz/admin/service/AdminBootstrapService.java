package com.jovensbuzz.admin.service;

import com.jovensbuzz.admin.entity.AdminRole;
import com.jovensbuzz.admin.entity.AdminUser;
import com.jovensbuzz.admin.repository.AdminUserRepository;
import com.jovensbuzz.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Creates the first administrator from environment configuration when the admin table is empty.
 * It never overwrites an existing account, so a password changed through the dashboard persists
 * across restarts. After provisioning, remove the bootstrap variables; sign-in uses only the database.
 */
@Service
public class AdminBootstrapService implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapService.class);
    private static final String KNOWN_DEV_PASSWORD = "Jovensbuzz@2026";

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.bootstrap.admin.username:}")
    private String bootstrapUsername;

    @Value("${app.bootstrap.admin.password:}")
    private String bootstrapPassword;

    @Value("${app.bootstrap.admin.email:admin@example.com}")
    private String bootstrapEmail;

    @Value("${app.bootstrap.admin.display-name:Administrator}")
    private String bootstrapDisplayName;

    @Value("${app.jwt.secret:" + JwtTokenProvider.DEFAULT_DEV_SECRET + "}")
    private String jwtSecret;

    @Value("${app.security.production-mode:false}")
    private boolean productionMode;

    public AdminBootstrapService(AdminUserRepository adminUserRepository, PasswordEncoder passwordEncoder) {
        this.adminUserRepository = adminUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        verifyProductionSafety();

        if (adminUserRepository.count() > 0) {
            log.info("Administrator accounts already exist; bootstrap skipped.");
            return;
        }
        if (!StringUtils.hasText(bootstrapUsername) || !StringUtils.hasText(bootstrapPassword)) {
            log.warn("No administrator exists and BOOTSTRAP_ADMIN_USERNAME / BOOTSTRAP_ADMIN_PASSWORD are not set. "
                    + "Set them and restart to create the first admin account.");
            return;
        }

        AdminUser admin = new AdminUser(
                bootstrapUsername.trim(),
                passwordEncoder.encode(bootstrapPassword),
                bootstrapDisplayName,
                bootstrapEmail.trim().toLowerCase(),
                AdminRole.OWNER.authority()
        );
        adminUserRepository.save(admin);
        log.info("Bootstrap owner '{}' created. Remove BOOTSTRAP_ADMIN_USERNAME/PASSWORD from the environment now.", admin.getUsername());
    }

    private void verifyProductionSafety() {
        if (!productionMode) {
            if (JwtTokenProvider.DEFAULT_DEV_SECRET.equals(jwtSecret)) {
                log.warn("Using the built-in development JWT secret. Set JWT_SECRET before deploying.");
            }
            return;
        }
        if (JwtTokenProvider.DEFAULT_DEV_SECRET.equals(jwtSecret) || jwtSecret == null || jwtSecret.length() < 44) {
            throw new IllegalStateException("Production mode requires a strong, non-default JWT_SECRET (base64, >= 32 bytes).");
        }
        if (KNOWN_DEV_PASSWORD.equals(bootstrapPassword)) {
            throw new IllegalStateException("Production mode refuses to start with the documented development bootstrap password.");
        }
    }
}
