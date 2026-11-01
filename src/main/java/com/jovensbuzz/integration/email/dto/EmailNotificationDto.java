package com.jovensbuzz.integration.email.dto;

import com.jovensbuzz.integration.email.entity.EmailNotificationJob;

import java.time.Instant;

/**
 * Admin-safe view of an email job (no message bodies or credentials).
 */
public record EmailNotificationDto(
        Long id,
        String notificationType,
        String recipientEmail,
        String subject,
        String status,
        int attemptCount,
        String lastError,
        Instant nextRetryAt,
        Instant sentAt,
        Instant updatedAt) {

    public static EmailNotificationDto fromEntity(EmailNotificationJob job) {
        return new EmailNotificationDto(
                job.getId(),
                job.getNotificationType().name(),
                job.getRecipientEmail(),
                job.getSubject(),
                job.getStatus().name(),
                job.getAttemptCount(),
                job.getLastError(),
                job.getNextRetryAt(),
                job.getSentAt(),
                job.getUpdatedAt());
    }
}
