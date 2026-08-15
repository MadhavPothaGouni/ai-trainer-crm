package com.aitrainercrm.platform.loyalty.repository;

import com.aitrainercrm.platform.loyalty.entity.LoyaltyTransaction;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoyaltyTransactionRepository extends JpaRepository<LoyaltyTransaction, UUID> {

    @Query("select l from LoyaltyTransaction l where l.id = :id and l.organizationId = :organizationId and l.deletedAt is null")
    Optional<LoyaltyTransaction> findActiveByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    Page<LoyaltyTransaction> findByOrganizationIdAndDeletedAtIsNull(UUID organizationId, Pageable pageable);

    Page<LoyaltyTransaction> findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(UUID organizationId, Set<UUID> ownerIds, Pageable pageable);

    @Query("select coalesce(sum(l.points), 0) from LoyaltyTransaction l "
            + "where l.organizationId = :organizationId and l.contactId = :contactId and l.deletedAt is null")
    long sumPointsByOrganizationIdAndContactId(@Param("organizationId") UUID organizationId, @Param("contactId") UUID contactId);

    @Query("select coalesce(sum(l.points), 0) from LoyaltyTransaction l where l.organizationId = :organizationId and l.contactId = :contactId "
            + "and l.ownerId in :ownerIds and l.deletedAt is null")
    long sumPointsByOrganizationIdAndContactIdAndOwnerIdIn(
            @Param("organizationId") UUID organizationId, @Param("contactId") UUID contactId, @Param("ownerIds") Set<UUID> ownerIds);
}
