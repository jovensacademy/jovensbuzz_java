package com.jovensbuzz.faq.repository;

import com.jovensbuzz.faq.entity.FaqItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FaqItemRepository extends JpaRepository<FaqItem, Long> {
    List<FaqItem> findByPublishedTrueOrderByDisplayOrderAsc();
    List<FaqItem> findAllByOrderByDisplayOrderAsc();
}
