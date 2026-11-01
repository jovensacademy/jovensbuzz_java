-- Flyway V3: Security hardening, lead assignment, duplicate protection and sync idempotency
-- V1/V2 are already applied in existing environments, so all changes are additive here.

-- Admin security: token revocation, forced password rotation, login lockout
ALTER TABLE admin_users
    ADD COLUMN token_version INT NOT NULL DEFAULT 0,
    ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN failed_login_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN locked_until TIMESTAMP NULL DEFAULT NULL,
    ADD COLUMN password_changed_at TIMESTAMP NULL DEFAULT NULL;

-- V2 seeded an admin row with a placeholder hash that matches no password. Remove it only if it
-- still carries that placeholder, so the environment-driven bootstrap creates a usable account.
DELETE FROM admin_users
WHERE username = 'jovens_buzz_2026'
  AND password_hash = '$2a$10$wE9aW691i/89J13f1k2KgeN8sE7bV8g6/gq7FvjQ9J9U6e3O4iMgy';

-- Accounts that never rotated their bootstrap password must do so
UPDATE admin_users SET must_change_password = TRUE WHERE password_changed_at IS NULL;

-- Enquiry assignment and duplicate-submission fingerprint
ALTER TABLE enquiries
    ADD COLUMN assigned_admin_id BIGINT NULL,
    ADD COLUMN submission_fingerprint VARCHAR(64) NULL,
    ADD CONSTRAINT fk_enquiries_assigned_admin FOREIGN KEY (assigned_admin_id) REFERENCES admin_users (id) ON DELETE SET NULL,
    ADD INDEX idx_enquiry_assigned_admin (assigned_admin_id),
    ADD INDEX idx_enquiry_fingerprint (submission_fingerprint, created_at),
    ADD INDEX idx_enquiry_status_created (status, created_at);

-- Sync jobs: record the spreadsheet range written, so retries never append twice
ALTER TABLE integration_sync_jobs
    ADD COLUMN external_ref VARCHAR(255) NULL,
    ADD INDEX idx_sync_enquiry_type (enquiry_id, integration_type);

-- Audit log lookups by entity
ALTER TABLE audit_logs
    ADD INDEX idx_audit_entity (entity_type, entity_id);

-- Additional editable public content keys
INSERT INTO site_content (content_key, content_value, content_type, updated_by) VALUES
('hero_primary_cta', 'Explore Services', 'TEXT', 'SYSTEM'),
('hero_secondary_cta', 'Let''s Talk', 'TEXT', 'SYSTEM'),
('about_body', 'Jovens Buzz is a social media growth and creative agency. We bring strategy, content, design and short-form video together under one team, so small businesses can show up consistently and look as credible online as the brands they compete with. Every post serves a purpose, every design builds recognition, and every plan is tied to a business goal.', 'TEXT', 'SYSTEM'),
('strategy_title', 'The Strategy Behind Every Successful Brand.', 'TEXT', 'SYSTEM'),
('strategy_description', 'Strategy decides what to say and to whom. Creative execution makes people stop and pay attention. Consistent communication builds the familiarity and trust that turn followers into enquiries. We plan all three together so your marketing compounds instead of starting over every month.', 'TEXT', 'SYSTEM'),
('footer_description', 'A creative, strategic and results-driven digital growth agency helping small businesses look big online.', 'TEXT', 'SYSTEM'),
('social_x', '', 'URL', 'SYSTEM')
ON DUPLICATE KEY UPDATE content_key = content_key;
