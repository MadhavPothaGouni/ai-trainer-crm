package com.aitrainercrm.platform.quote.repository;

import com.aitrainercrm.platform.quote.entity.Quote;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuoteRepository extends JpaRepository<Quote, UUID> {

    @Query("select q from Quote q where q.id = :id and q.organizationId = :organizationId and q.deletedAt is null")
    Optional<Quote> findActiveByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    Page<Quote> findByOrganizationIdAndDeletedAtIsNull(UUID organizationId, Pageable pageable);

    Page<Quote> findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(UUID organizationId, Set<UUID> ownerIds, Pageable pageable);

    Page<Quote> findByOrganizationIdAndOpportunityIdAndDeletedAtIsNull(UUID organizationId, UUID opportunityId, Pageable pageable);

    Page<Quote> findByOrganizationIdAndOwnerIdInAndOpportunityIdAndDeletedAtIsNull(
            UUID organizationId, Set<UUID> ownerIds, UUID opportunityId, Pageable pageable);

    /** Added for ApprovalRequestService#validateRelatedTo - QUOTE is a valid relatedToType for approval requests, same existence-check shape AccountRepository/ContactRepository/.../TicketRepository already had. */
    boolean existsByIdAndOrganizationIdAndDeletedAtIsNull(UUID id, UUID organizationId);
}
