package com.aitrainercrm.platform.certification.repository;

import com.aitrainercrm.platform.certification.entity.UserCertification;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserCertificationRepository extends JpaRepository<UserCertification, UUID> {

    @Query("select u from UserCertification u where u.id = :id and u.organizationId = :organizationId and u.deletedAt is null")
    Optional<UserCertification> findActiveByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    Page<UserCertification> findByOrganizationIdAndDeletedAtIsNullOrderByEarnedAtDesc(UUID organizationId, Pageable pageable);

    Page<UserCertification> findByOrganizationIdAndUserIdInAndDeletedAtIsNullOrderByEarnedAtDesc(
            UUID organizationId, Set<UUID> userIds, Pageable pageable);
}
