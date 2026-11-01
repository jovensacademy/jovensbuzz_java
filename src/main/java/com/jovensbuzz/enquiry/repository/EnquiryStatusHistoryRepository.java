package com.jovensbuzz.enquiry.repository;

import com.jovensbuzz.enquiry.entity.EnquiryStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EnquiryStatusHistoryRepository extends JpaRepository<EnquiryStatusHistory, Long> {
    List<EnquiryStatusHistory> findByEnquiryIdOrderByChangedAtDesc(Long enquiryId);
}
