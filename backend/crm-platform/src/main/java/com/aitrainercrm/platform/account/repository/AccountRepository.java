package com.aitrainercrm.platform.account.repository;

import com.aitrainercrm.platform.account.entity.Account;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    @Query("select a from Account a where a.id = :id and a.organizationId = :organizationId and a.deletedAt is null")
    Optional<Account> findActiveByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    Page<Account> findByOrganizationIdAndDeletedAtIsNull(UUID organizationId, Pageable pageable);

    Page<Account> findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(UUID organizationId, Set<UUID> ownerIds, Pageable pageable);

    /** Unpaginated variants for CSV export - an export needs every visible row in one pass, not a page at a time (same reasoning CampaignService#exportCsv documents). */
    List<Account> findByOrganizationIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID organizationId);

    List<Account> findByOrganizationIdAndOwnerIdInAndDeletedAtIsNullOrderByCreatedAtDesc(UUID organizationId, Set<UUID> ownerIds);

    /** Existence check that respects tenant + soft-delete, used when another entity (Contact, Opportunity) is asked to link to an account id. */
    boolean existsByIdAndOrganizationIdAndDeletedAtIsNull(UUID id, UUID organizationId);

    /** DuplicateDetectionListener's only candidate search for Account - name is the one field every Account has, so there's no email-vs-name branch here the way Lead/Contact have. */
    @Query(
            "select a from Account a where a.organizationId = :organizationId and lower(a.name) = lower(:name) "
                    + "and a.id <> :excludeId and a.deletedAt is null")
    List<Account> findDuplicateCandidatesByName(
            @Param("organizationId") UUID organizationId, @Param("name") String name, @Param("excludeId") UUID excludeId);
}
