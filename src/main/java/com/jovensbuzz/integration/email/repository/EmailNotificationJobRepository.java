package com.jovensbuzz.integration.email.repository;

import com.jovensbuzz.integration.email.entity.EmailNotificationJob;
import com.jovensbuzz.integration.email.entity.EmailNotificationStatus;
import com.jovensbuzz.integration.email.entity.EmailNotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface EmailNotificationJobRepository extends JpaRepository<EmailNotificationJob, Long> {

    List<EmailNotificationJob> findByEnquiryIdOrderByNotificationTypeAsc(Long enquiryId);

    Optional<EmailNotificationJob> findByEnquiryIdAndNotificationType(Long enquiryId, EmailNotificationType type);

    /**
     * Atomically moves a due PENDING job to PROCESSING. Only one worker can win, so an email
     * is never sent twice by concurrent workers (post-commit listener vs. scheduler vs. admin retry).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE EmailNotificationJob j SET j.status = :processing, j.updatedAt = :now " +
           "WHERE j.id = :id AND j.status = :pending AND (j.nextRetryAt IS NULL OR j.nextRetryAt <= :now)")
    int claim(@Param("id") Long id,
              @Param("pending") EmailNotificationStatus pending,
              @Param("processing") EmailNotificationStatus processing,
              @Param("now") Instant now);

    @Query("SELECT j.id FROM EmailNotificationJob j WHERE j.status = :pending " +
           "AND (j.nextRetryAt IS NULL OR j.nextRetryAt <= :now) ORDER BY j.createdAt ASC")
    List<Long> findDueJobIds(@Param("pending") EmailNotificationStatus pending, @Param("now") Instant now);

    /** Returns jobs stuck in PROCESSING (e.g. after a crash) to the queue. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE EmailNotificationJob j SET j.status = :pending, j.updatedAt = :now " +
           "WHERE j.status = :processing AND j.updatedAt < :staleBefore")
    int recoverStale(@Param("processing") EmailNotificationStatus processing,
                     @Param("pending") EmailNotificationStatus pending,
                     @Param("staleBefore") Instant staleBefore,
                     @Param("now") Instant now);

    long countByStatus(EmailNotificationStatus status);
}
