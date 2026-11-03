-- Owner / Manager roles, per-user permissions, per-user display time zone and soft deletion.
-- Additive only: no existing column or row is removed.

ALTER TABLE admin_users
    -- Comma-separated permission names for MANAGER accounts; OWNER accounts implicitly hold every permission
    ADD COLUMN permissions VARCHAR(512) NULL AFTER role,
    -- IANA time zone used to display dates for this user (never a fixed offset)
    ADD COLUMN timezone VARCHAR(64) NOT NULL DEFAULT 'America/Chicago' AFTER permissions,
    -- Soft deletion keeps audit history, notes and assignments intact
    ADD COLUMN deleted_at TIMESTAMP NULL DEFAULT NULL,
    ADD COLUMN deleted_by VARCHAR(64) NULL;

-- Existing administrators become owners (the bootstrap account belongs to the USA owner)
UPDATE admin_users SET role = 'ROLE_OWNER' WHERE role = 'ROLE_ADMIN';

CREATE INDEX idx_admin_users_role_active ON admin_users (role, enabled, deleted_at);
