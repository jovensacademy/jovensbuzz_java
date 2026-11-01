package com.jovensbuzz.integration.email.entity;

import com.jovensbuzz.enquiry.entity.Enquiry;
import jakarta.persistence.*;

import java.time.Instant;

/**
 * Durable outbox record for one email about one enquiry. The email body is rendered from the
 * enquiry at send time, so only metadata (never credentials or message bodies) is stored here.
 */
@Entity
@Table(name = "email_notification_jobs")
public class EmailNotificationJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "enquiry_id", nullable = false)
    private Enquiry enquiry;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 48)
    private EmailNotificationType notificationType;

    // Null for admin notifications: the configured admin address is resolved at send time
    @Column(name = "recipient_email")
    private String recipientEmail;

    @Column(nullable = false)
    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EmailNotificationStatus status = EmailNotificationStatus.PENDING;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public EmailNotificationJob() {}

    public EmailNotificationJob(Enquiry enquiry, EmailNotificationType type, String recipientEmail, String subject) {
        this.enquiry = enquiry;
        this.notificationType = type;
        this.recipientEmail = recipientEmail;
        this.subject = subject;
        this.status = EmailNotificationStatus.PENDING;
    }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Enquiry getEnquiry() {
        return enquiry;
    }

    public void setEnquiry(Enquiry enquiry) {
        this.enquiry = enquiry;
    }

    public EmailNotificationType getNotificationType() {
        return notificationType;
    }

    public void setNotificationType(EmailNotificationType notificationType) {
        this.notificationType = notificationType;
    }

    public String getRecipientEmail() {
        return recipientEmail;
    }

    public void setRecipientEmail(String recipientEmail) {
        this.recipientEmail = recipientEmail;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public EmailNotificationStatus getStatus() {
        return status;
    }

    public void setStatus(EmailNotificationStatus status) {
        this.status = status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public Instant getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(Instant nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public void setSentAt(Instant sentAt) {
        this.sentAt = sentAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
