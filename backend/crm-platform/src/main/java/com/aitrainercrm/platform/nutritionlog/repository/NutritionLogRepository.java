package com.aitrainercrm.platform.nutritionlog.repository;

import com.aitrainercrm.platform.nutritionlog.entity.NutritionLog;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NutritionLogRepository extends JpaRepository<NutritionLog, UUID> {

    @Query("select n from NutritionLog n where n.id = :id and n.organizationId = :organizationId and n.deletedAt is null")
    Optional<NutritionLog> findActiveByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    Page<NutritionLog> findByOrganizationIdAndDeletedAtIsNull(UUID organizationId, Pageable pageable);

    Page<NutritionLog> findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(UUID organizationId, Set<UUID> ownerIds, Pageable pageable);
}
