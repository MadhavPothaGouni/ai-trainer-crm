package com.aitrainercrm.platform.activity.repository;

import com.aitrainercrm.platform.activity.entity.Activity;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ActivityRepository extends JpaRepository<Activity, UUID> {

    @Query("select a from Activity a where a.id = :id and a.organizationId = :organizationId")
    Optional<Activity> findByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    Page<Activity> findByOrganizationId(UUID organizationId, Pageable pageable);

    Page<Activity> findByOrganizationIdAndOwnerIdIn(UUID organizationId, Set<UUID> ownerIds, Pageable pageable);

    Page<Activity> findByOrganizationIdAndRelatedToTypeAndRelatedToId(
            UUID organizationId, Activity.RelatedToType relatedToType, UUID relatedToId, Pageable pageable);

    Page<Activity> findByOrganizationIdAndOwnerIdInAndRelatedToTypeAndRelatedToId(
            UUID organizationId, Set<UUID> ownerIds, Activity.RelatedToType relatedToType, UUID relatedToId, Pageable pageable);
}
