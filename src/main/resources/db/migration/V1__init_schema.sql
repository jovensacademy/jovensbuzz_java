-- Flyway V1: Initial Database Schema for Jovens Buzz
-- Database: jovens_buzz (MySQL 8.x / MariaDB compatible)

CREATE TABLE IF NOT EXISTS admin_users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    email VARCHAR(128) NOT NULL,
    role VARCHAR(32) NOT NULL DEFAULT 'ROLE_ADMIN',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP NULL,
    INDEX idx_admin_username (username),
    INDEX idx_admin_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS enquiries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_reference VARCHAR(36) NOT NULL UNIQUE,
    full_name VARCHAR(128) NOT NULL,
    email VARCHAR(128) NOT NULL,
    phone VARCHAR(32) NULL,
    company_name VARCHAR(128) NULL,
    service_interest VARCHAR(128) NOT NULL,
    budget VARCHAR(64) NULL,
    message TEXT NULL,
    privacy_consent BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(32) NOT NULL DEFAULT 'NEW',
    source VARCHAR(64) NOT NULL DEFAULT 'WEBSITE_LANDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_enquiry_status (status),
    INDEX idx_enquiry_created_at (created_at),
    INDEX idx_enquiry_service (service_interest),
    INDEX idx_enquiry_email (email),
    INDEX idx_enquiry_reference (public_reference)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS enquiry_notes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    enquiry_id BIGINT NOT NULL,
    admin_user_id BIGINT NULL,
    note TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_enquiry_notes_enquiry FOREIGN KEY (enquiry_id) REFERENCES enquiries (id) ON DELETE CASCADE,
    CONSTRAINT fk_enquiry_notes_admin FOREIGN KEY (admin_user_id) REFERENCES admin_users (id) ON DELETE SET NULL,
    INDEX idx_enquiry_notes_enquiry_id (enquiry_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS enquiry_status_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    enquiry_id BIGINT NOT NULL,
    changed_by VARCHAR(64) NOT NULL,
    previous_status VARCHAR(32) NULL,
    new_status VARCHAR(32) NOT NULL,
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_enquiry_status_history_enquiry FOREIGN KEY (enquiry_id) REFERENCES enquiries (id) ON DELETE CASCADE,
    INDEX idx_status_history_enquiry_id (enquiry_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS services (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(128) NOT NULL,
    slug VARCHAR(128) NOT NULL UNIQUE,
    description TEXT NOT NULL,
    icon VARCHAR(64) NULL,
    display_order INT NOT NULL DEFAULT 0,
    published BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_services_display_order (display_order),
    INDEX idx_services_published (published)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS faqs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    question VARCHAR(255) NOT NULL,
    answer TEXT NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    published BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_faqs_display_order (display_order),
    INDEX idx_faqs_published (published)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS site_content (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    content_key VARCHAR(64) NOT NULL UNIQUE,
    content_value MEDIUMTEXT NOT NULL,
    content_type VARCHAR(32) NOT NULL DEFAULT 'TEXT',
    updated_by VARCHAR(64) NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_site_content_key (content_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS integration_sync_jobs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    enquiry_id BIGINT NOT NULL,
    integration_type VARCHAR(32) NOT NULL DEFAULT 'GOOGLE_SHEETS',
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    last_error TEXT NULL,
    next_retry_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_sync_jobs_enquiry FOREIGN KEY (enquiry_id) REFERENCES enquiries (id) ON DELETE CASCADE,
    INDEX idx_sync_status (status),
    INDEX idx_sync_next_retry (next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    admin_user_id BIGINT NULL,
    action VARCHAR(64) NOT NULL,
    entity_type VARCHAR(64) NOT NULL,
    entity_id VARCHAR(64) NULL,
    safe_metadata TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_logs_admin FOREIGN KEY (admin_user_id) REFERENCES admin_users (id) ON DELETE SET NULL,
    INDEX idx_audit_action (action),
    INDEX idx_audit_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
