package com.aitrainercrm.platform.membership.repository;

import com.aitrainercrm.platform.membership.entity.MembershipPlan;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MembershipPlanRepository extends JpaRepository<MembershipPlan, UUID> {

    @Query("select p from MembershipPlan p where p.id = :id and p.organizationId = :organizationId and p.deletedAt is null")
    Optional<MembershipPlan> findActiveByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    Page<MembershipPlan> findByOrganizationIdAndDeletedAtIsNull(UUID organizationId, Pageable pageable);

    /** Existence + tenant check used by MembershipService when a membership references a plan. */
    boolean existsByIdAndOrganizationIdAndDeletedAtIsNull(UUID id, UUID organizationId);
}
