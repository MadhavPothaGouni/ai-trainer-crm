package com.aitrainercrm.platform.groupclass.repository;

import com.aitrainercrm.platform.groupclass.entity.GroupClass;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupClassRepository extends JpaRepository<GroupClass, UUID> {

    @Query("select g from GroupClass g where g.id = :id and g.organizationId = :organizationId and g.deletedAt is null")
    Optional<GroupClass> findActiveByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    Page<GroupClass> findByOrganizationIdAndDeletedAtIsNull(UUID organizationId, Pageable pageable);

    boolean existsByIdAndOrganizationIdAndDeletedAtIsNull(UUID id, UUID organizationId);
}
