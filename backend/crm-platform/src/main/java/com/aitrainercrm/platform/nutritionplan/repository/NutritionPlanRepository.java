package com.aitrainercrm.platform.nutritionplan.repository;

import com.aitrainercrm.platform.nutritionplan.entity.NutritionPlan;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NutritionPlanRepository extends JpaRepository<NutritionPlan, UUID> {

    @Query("select p from NutritionPlan p where p.id = :id and p.organizationId = :organizationId and p.deletedAt is null")
    Optional<NutritionPlan> findActiveByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    Page<NutritionPlan> findByOrganizationIdAndDeletedAtIsNull(UUID organizationId, Pageable pageable);

    Page<NutritionPlan> findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(UUID organizationId, Set<UUID> ownerIds, Pageable pageable);
}
