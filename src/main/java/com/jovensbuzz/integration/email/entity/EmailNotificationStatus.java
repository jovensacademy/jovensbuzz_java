package com.jovensbuzz.integration.email.entity;

/**
 * PENDING jobs wait for delivery (possibly with a next_retry_at after a temporary failure, or
 * because SMTP is not configured yet). FAILED means retries are exhausted; an admin can re-queue.
 */
public enum EmailNotificationStatus {
    PENDING,
    PROCESSING,
    SENT,
    FAILED
}
