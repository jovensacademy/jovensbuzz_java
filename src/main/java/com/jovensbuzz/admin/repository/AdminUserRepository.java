package com.jovensbuzz.admin.repository;

import com.jovensbuzz.admin.entity.AdminUser;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AdminUserRepository extends JpaRepository<AdminUser, Long> {
    Optional<AdminUser> findByUsername(String username);
    Optional<AdminUser> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);

    /** Every account that has not been soft-deleted (enabled or disabled). */
    List<AdminUser> findByDeletedAtIsNullOrderByCreatedAtAsc();

    Optional<AdminUser> findFirstByEmailIgnoreCaseAndDeletedAtIsNull(String email);

    /**
     * Active owners, row-locked so two concurrent changes can never both remove "the last owner".
     * ROLE_ADMIN is the pre-V7 name of the owner role.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM AdminUser a WHERE a.role IN ('ROLE_OWNER', 'ROLE_ADMIN') AND a.enabled = true AND a.deletedAt IS NULL")
    List<AdminUser> lockActiveOwners();
}
