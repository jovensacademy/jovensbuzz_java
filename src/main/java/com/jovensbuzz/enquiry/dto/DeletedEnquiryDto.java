package com.jovensbuzz.enquiry.dto;

import com.jovensbuzz.enquiry.entity.Enquiry;

import java.time.Instant;

/** A soft-deleted enquiry as shown in the recovery list. */
public record DeletedEnquiryDto(
        Long id,
        String publicReference,
        String fullName,
        String email,
        String serviceInterest,
        String status,
        Instant createdAt,
        Instant deletedAt,
        String deletedBy) {

    public static DeletedEnquiryDto fromEntity(Enquiry e) {
        return new DeletedEnquiryDto(e.getId(), e.getPublicReference(), e.getFullName(), e.getEmail(),
                e.getServiceInterest(), e.getStatus() != null ? e.getStatus().name() : null,
                e.getCreatedAt(), e.getDeletedAt(), e.getDeletedBy());
    }
}
