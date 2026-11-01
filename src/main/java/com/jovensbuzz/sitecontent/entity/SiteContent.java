package com.jovensbuzz.sitecontent.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "site_content")
public class SiteContent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "content_key", nullable = false, unique = true, length = 64)
    private String contentKey;

    @Column(name = "content_value", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String contentValue;

    @Column(name = "content_type", nullable = false, length = 32)
    private String contentType = "TEXT";

    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public SiteContent() {}

    public SiteContent(String contentKey, String contentValue, String contentType, String updatedBy) {
        this.contentKey = contentKey;
        this.contentValue = contentValue;
        this.contentType = contentType != null ? contentType : "TEXT";
        this.updatedBy = updatedBy;
        this.updatedAt = Instant.now();
    }

    @PrePersist
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getContentKey() {
        return contentKey;
    }

    public void setContentKey(String contentKey) {
        this.contentKey = contentKey;
    }

    public String getContentValue() {
        return contentValue;
    }

    public void setContentValue(String contentValue) {
        this.contentValue = contentValue;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
