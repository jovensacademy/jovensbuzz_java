-- Flyway V4: International phone country + durable email notification jobs (outbox)

-- ISO 3166-1 alpha-2 country chosen for the phone number (US and CA share +1, so the dial
-- code alone cannot identify the country). Phone numbers are stored in E.164 format.
ALTER TABLE enquiries
    ADD COLUMN phone_country VARCHAR(2) NULL AFTER phone;

CREATE TABLE IF NOT EXISTS email_notification_jobs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    enquiry_id BIGINT NOT NULL,
    notification_type VARCHAR(48) NOT NULL,
    -- NULL for admin notifications: the configured admin address is resolved at send time
    recipient_email VARCHAR(255) NULL,
    subject VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    last_error VARCHAR(500) NULL,
    next_retry_at TIMESTAMP NULL DEFAULT NULL,
    sent_at TIMESTAMP NULL DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_email_jobs_enquiry FOREIGN KEY (enquiry_id) REFERENCES enquiries (id) ON DELETE CASCADE,
    -- One job per enquiry and type: retries reuse the row, so an email can never be queued twice
    CONSTRAINT uq_email_jobs_enquiry_type UNIQUE (enquiry_id, notification_type),
    INDEX idx_email_jobs_status_retry (status, next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
