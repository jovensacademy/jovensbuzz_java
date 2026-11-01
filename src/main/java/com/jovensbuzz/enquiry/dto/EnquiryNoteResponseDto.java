package com.jovensbuzz.enquiry.dto;

import com.jovensbuzz.enquiry.entity.EnquiryNote;
import java.time.Instant;

public class EnquiryNoteResponseDto {
    private Long id;
    private String adminUsername;
    private String adminDisplayName;
    private String note;
    private Instant createdAt;

    public EnquiryNoteResponseDto() {}

    public static EnquiryNoteResponseDto fromEntity(EnquiryNote note) {
        EnquiryNoteResponseDto dto = new EnquiryNoteResponseDto();
        dto.setId(note.getId());
        if (note.getAdminUser() != null) {
            dto.setAdminUsername(note.getAdminUser().getUsername());
            dto.setAdminDisplayName(note.getAdminUser().getDisplayName());
        } else {
            dto.setAdminUsername("SYSTEM");
            dto.setAdminDisplayName("System Administrator");
        }
        dto.setNote(note.getNote());
        dto.setCreatedAt(note.getCreatedAt());
        return dto;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAdminUsername() {
        return adminUsername;
    }

    public void setAdminUsername(String adminUsername) {
        this.adminUsername = adminUsername;
    }

    public String getAdminDisplayName() {
        return adminDisplayName;
    }

    public void setAdminDisplayName(String adminDisplayName) {
        this.adminDisplayName = adminDisplayName;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
