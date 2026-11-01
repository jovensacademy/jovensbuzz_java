package com.jovensbuzz.enquiry.repository;

import com.jovensbuzz.enquiry.entity.EnquiryNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EnquiryNoteRepository extends JpaRepository<EnquiryNote, Long> {
    List<EnquiryNote> findByEnquiryIdOrderByCreatedAtDesc(Long enquiryId);
}
