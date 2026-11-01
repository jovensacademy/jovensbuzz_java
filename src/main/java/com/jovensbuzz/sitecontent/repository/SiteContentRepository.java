package com.jovensbuzz.sitecontent.repository;

import com.jovensbuzz.sitecontent.entity.SiteContent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SiteContentRepository extends JpaRepository<SiteContent, Long> {
    Optional<SiteContent> findByContentKey(String contentKey);
    boolean existsByContentKey(String contentKey);
}
