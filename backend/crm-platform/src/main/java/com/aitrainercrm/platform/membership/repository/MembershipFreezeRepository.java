package com.aitrainercrm.platform.membership.repository;

import com.aitrainercrm.platform.membership.entity.MembershipFreeze;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MembershipFreezeRepository extends JpaRepository<MembershipFreeze, UUID> {

    @Query("select f from MembershipFreeze f where f.id = :id and f.organizationId = :organizationId and f.deletedAt is null")
    Optional<MembershipFreeze> findActiveByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    Page<MembershipFreeze> findByOrganizationIdAndDeletedAtIsNull(UUID organizationId, Pageable pageable);

    Page<MembershipFreeze> findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(UUID organizationId, Set<UUID> ownerIds, Pageable pageable);

    /** Used by {@code MembershipFreezeService#assertNoOverlap} when creating a new freeze (nothing to exclude yet). */
    boolean existsByMembershipIdAndStatusInAndDeletedAtIsNullAndFreezeStartLessThanAndFreezeEndGreaterThan(
            UUID membershipId, Collection<MembershipFreeze.Status> statuses, LocalDate freezeStartLessThan, LocalDate freezeEndGreaterThan);

    /** Same overlap check as above, but excluding the freeze being updated/re-activated. */
    boolean existsByMembershipIdAndStatusInAndDeletedAtIsNullAndIdNotAndFreezeStartLessThanAndFreezeEndGreaterThan(
            UUID membershipId,
            Collection<MembershipFreeze.Status> statuses,
            UUID excludeId,
            LocalDate freezeStartLessThan,
            LocalDate freezeEndGreaterThan);
}
