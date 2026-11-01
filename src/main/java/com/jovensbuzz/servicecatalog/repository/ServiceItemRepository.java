package com.jovensbuzz.servicecatalog.repository;

import com.jovensbuzz.servicecatalog.entity.ServiceItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceItemRepository extends JpaRepository<ServiceItem, Long> {
    List<ServiceItem> findByPublishedTrueOrderByDisplayOrderAsc();
    List<ServiceItem> findAllByOrderByDisplayOrderAsc();
    Optional<ServiceItem> findBySlug(String slug);
    boolean existsBySlug(String slug);
}
