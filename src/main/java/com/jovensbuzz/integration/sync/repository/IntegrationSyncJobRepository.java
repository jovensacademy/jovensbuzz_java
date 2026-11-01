package com.jovensbuzz.integration.sync.repository;

import com.jovensbuzz.integration.sync.entity.IntegrationSyncJob;
import com.jovensbuzz.integration.sync.entity.SyncStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface IntegrationSyncJobRepository extends JpaRepository<IntegrationSyncJob, Long> {

    Optional<IntegrationSyncJob> findTopByEnquiryIdAndIntegrationTypeOrderByCreatedAtDesc(
            Long enquiryId, String integrationType);

    List<IntegrationSyncJob> findByEnquiryIdOrderByCreatedAtDesc(Long enquiryId);

    @Query("SELECT j FROM IntegrationSyncJob j WHERE j.enquiry.id IN :enquiryIds AND j.integrationType = :type")
    List<IntegrationSyncJob> findByEnquiryIdsAndType(@Param("enquiryIds") Collection<Long> enquiryIds,
                                                     @Param("type") String integrationType);

    /**
     * Atomically moves a job into PROCESSING. Returns 1 only for the single worker that wins
     * the claim, so the scheduler and the post-submit worker can never process a job twice.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE IntegrationSyncJob j SET j.status = :processing, j.updatedAt = :now " +
           "WHERE j.id = :id AND j.status IN :claimable")
    int claim(@Param("id") Long id,
              @Param("claimable") Collection<SyncStatus> claimable,
              @Param("processing") SyncStatus processing,
              @Param("now") Instant now);

    @Query("SELECT j.id FROM IntegrationSyncJob j WHERE " +
           "(j.status = :pending AND j.createdAt <= :pendingCutoff) " +
           "OR (j.status = :retrying AND (j.nextRetryAt IS NULL OR j.nextRetryAt <= :now)) " +
           "ORDER BY j.createdAt ASC")
    List<Long> findDueJobIds(@Param("pending") SyncStatus pending,
                             @Param("retrying") SyncStatus retrying,
                             @Param("pendingCutoff") Instant pendingCutoff,
                             @Param("now") Instant now);

    /**
     * Returns jobs stuck in PROCESSING (e.g. after a crash) to the retry queue.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE IntegrationSyncJob j SET j.status = :retrying, j.nextRetryAt = :now, j.updatedAt = :now " +
           "WHERE j.status = :processing AND j.updatedAt < :staleBefore")
    int recoverStale(@Param("processing") SyncStatus processing,
                     @Param("retrying") SyncStatus retrying,
                     @Param("staleBefore") Instant staleBefore,
                     @Param("now") Instant now);

    /**
     * Prefix match so jobs stamped with older wordings of the same error are still found.
     */
    @Query("SELECT j.id FROM IntegrationSyncJob j WHERE j.status = :failed AND j.lastError LIKE CONCAT(:prefix, '%')")
    List<Long> findIdsByStatusAndLastErrorStartingWith(@Param("failed") SyncStatus failed, @Param("prefix") String prefix);

    List<IntegrationSyncJob> findByStatus(SyncStatus status);

    long countByStatus(SyncStatus status);
}
