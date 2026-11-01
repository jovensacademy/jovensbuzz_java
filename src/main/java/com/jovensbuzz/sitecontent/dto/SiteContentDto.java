package com.jovensbuzz.sitecontent.dto;

import java.time.Instant;

import com.jovensbuzz.sitecontent.entity.SiteContent;
import jakarta.validation.constraints.NotBlank;

public class SiteContentDto {
    private Long id;

    @NotBlank(message = "Content key is required")
    private String contentKey;

    @NotBlank(message = "Content value is required")
    private String contentValue;

    private String contentType;
    private String updatedBy;
    private String label;
    private String group;
    private int maxLength;
    private boolean required;
    private Instant updatedAt;

    public SiteContentDto() {}

    public static SiteContentDto fromEntity(SiteContent entity) {
        SiteContentDto dto = new SiteContentDto();
        dto.setId(entity.getId());
        dto.setContentKey(entity.getContentKey());
        dto.setContentValue(entity.getContentValue());
        dto.setContentType(entity.getContentType());
        dto.setUpdatedBy(entity.getUpdatedBy());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
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

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public int getMaxLength() {
        return maxLength;
    }

    public void setMaxLength(int maxLength) {
        this.maxLength = maxLength;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
