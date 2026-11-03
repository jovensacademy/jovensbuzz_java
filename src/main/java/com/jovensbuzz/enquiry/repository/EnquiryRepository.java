package com.jovensbuzz.enquiry.repository;

import com.jovensbuzz.enquiry.entity.Enquiry;
import com.jovensbuzz.enquiry.entity.EnquiryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface EnquiryRepository extends JpaRepository<Enquiry, Long>, JpaSpecificationExecutor<Enquiry> {

    Optional<Enquiry> findByPublicReference(String publicReference);

    /** Includes soft-deleted rows: references are UNIQUE in the table, so a new one must not reuse a deleted one. */
    @Query(value = "SELECT COUNT(*) FROM enquiries WHERE public_reference = :ref", nativeQuery = true)
    long countByPublicReferenceIncludingDeleted(@Param("ref") String publicReference);

    default boolean existsByPublicReference(String publicReference) {
        return countByPublicReferenceIncludingDeleted(publicReference) > 0;
    }

    Optional<Enquiry> findFirstBySubmissionFingerprintAndCreatedAtAfterOrderByCreatedAtDesc(
            String submissionFingerprint, Instant after);

    long countByStatus(EnquiryStatus status);

    long countByCreatedAtAfter(Instant after);

    long countByCreatedAtBetween(Instant start, Instant end);

    @Query("SELECT e.serviceInterest, COUNT(e) FROM Enquiry e " +
           "WHERE (:from IS NULL OR e.createdAt >= :from) AND (:to IS NULL OR e.createdAt < :to) " +
           "GROUP BY e.serviceInterest ORDER BY COUNT(e) DESC")
    List<Object[]> countByServiceInterestGrouped(@Param("from") Instant from, @Param("to") Instant to);

    @Query("SELECT e.status, COUNT(e) FROM Enquiry e " +
           "WHERE (:from IS NULL OR e.createdAt >= :from) AND (:to IS NULL OR e.createdAt < :to) " +
           "GROUP BY e.status")
    List<Object[]> countByStatusGrouped(@Param("from") Instant from, @Param("to") Instant to);

    @Query("SELECT e.createdAt FROM Enquiry e WHERE e.createdAt >= :from AND e.createdAt < :to")
    List<Instant> findCreatedAtBetween(@Param("from") Instant from, @Param("to") Instant to);

    @EntityGraph(attributePaths = "assignedAdmin")
    @Query(value = "SELECT e FROM Enquiry e WHERE " +
           "(:search IS NULL OR LOWER(e.fullName) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(e.email) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(e.companyName) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(e.publicReference) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND (:status IS NULL OR e.status = :status) " +
           "AND (:service IS NULL OR e.serviceInterest = :service) " +
           "AND (:assignedAdminId IS NULL OR e.assignedAdmin.id = :assignedAdminId) " +
           "AND (:startDate IS NULL OR e.createdAt >= :startDate) " +
           "AND (:endDate IS NULL OR e.createdAt <= :endDate)",
           countQuery = "SELECT COUNT(e) FROM Enquiry e WHERE " +
           "(:search IS NULL OR LOWER(e.fullName) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(e.email) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(e.companyName) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(e.publicReference) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND (:status IS NULL OR e.status = :status) " +
           "AND (:service IS NULL OR e.serviceInterest = :service) " +
           "AND (:assignedAdminId IS NULL OR e.assignedAdmin.id = :assignedAdminId) " +
           "AND (:startDate IS NULL OR e.createdAt >= :startDate) " +
           "AND (:endDate IS NULL OR e.createdAt <= :endDate)")
    Page<Enquiry> searchAndFilter(
            @Param("search") String search,
            @Param("status") EnquiryStatus status,
            @Param("service") String service,
            @Param("assignedAdminId") Long assignedAdminId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "assignedAdmin")
    @Query("SELECT e FROM Enquiry e ORDER BY e.createdAt DESC")
    List<Enquiry> findRecentEnquiries(Pageable pageable);

    @Query("SELECT DISTINCT e.serviceInterest FROM Enquiry e ORDER BY e.serviceInterest")
    List<String> findDistinctServiceInterests();

    /** [fingerprint, publicReference] pairs for existing enquiries with any of the given fingerprints. */
    @Query("SELECT e.submissionFingerprint, e.publicReference FROM Enquiry e WHERE e.submissionFingerprint IN :fingerprints")
    List<Object[]> findReferencesByFingerprints(@Param("fingerprints") java.util.Collection<String> fingerprints);

    // ---------------------------------------------------------------- soft-deleted enquiries (native: bypass @SQLRestriction)

    @Query(value = "SELECT * FROM enquiries WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC LIMIT 500", nativeQuery = true)
    List<Enquiry> findDeleted();

    /** [publicReference, fullName, deletedAt, deletedBy] of a soft-deleted enquiry, row-locked for permanent deletion. */
    @Query(value = "SELECT public_reference, full_name, deleted_at, deleted_by FROM enquiries WHERE id = :id AND deleted_at IS NOT NULL FOR UPDATE",
            nativeQuery = true)
    List<Object[]> lockDeletedForPurge(@Param("id") Long id);

    /** Hard delete of a soft-deleted enquiry. Notes, status history and job rows go with it (ON DELETE CASCADE). */
    @org.springframework.data.jpa.repository.Modifying
    @Query(value = "DELETE FROM enquiries WHERE id = :id AND deleted_at IS NOT NULL", nativeQuery = true)
    int purgeDeleted(@Param("id") Long id);

    @Query(value = "SELECT public_reference, full_name FROM enquiries WHERE id = :id", nativeQuery = true)
    List<Object[]> findReferenceAndNameIncludingDeleted(@Param("id") Long id);
}
