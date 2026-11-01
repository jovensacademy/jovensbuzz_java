package com.jovensbuzz.enquiry.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "enquiry_status_history")
public class EnquiryStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "enquiry_id", nullable = false)
    private Enquiry enquiry;

    @Column(name = "changed_by", nullable = false, length = 64)
    private String changedBy;

    @Column(name = "previous_status", length = 32)
    private String previousStatus;

    @Column(name = "new_status", nullable = false, length = 32)
    private String newStatus;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt = Instant.now();

    public EnquiryStatusHistory() {}

    public EnquiryStatusHistory(Enquiry enquiry, String changedBy, String previousStatus, String newStatus) {
        this.enquiry = enquiry;
        this.changedBy = changedBy;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.changedAt = Instant.now();
    }

    @PrePersist
    public void prePersist() {
        if (changedAt == null) changedAt = Instant.now();
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

    public String getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(String changedBy) {
        this.changedBy = changedBy;
    }

    public String getPreviousStatus() {
        return previousStatus;
    }

    public void setPreviousStatus(String previousStatus) {
        this.previousStatus = previousStatus;
    }

    public String getNewStatus() {
        return newStatus;
    }

    public void setNewStatus(String newStatus) {
        this.newStatus = newStatus;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(Instant changedAt) {
        this.changedAt = changedAt;
    }
}
