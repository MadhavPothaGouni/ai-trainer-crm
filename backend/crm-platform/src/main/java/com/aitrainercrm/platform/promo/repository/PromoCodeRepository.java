package com.aitrainercrm.platform.promo.repository;

import com.aitrainercrm.platform.promo.entity.PromoCode;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PromoCodeRepository extends JpaRepository<PromoCode, UUID> {

    @Query("select p from PromoCode p where p.id = :id and p.organizationId = :organizationId and p.deletedAt is null")
    Optional<PromoCode> findActiveByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    Page<PromoCode> findByOrganizationIdAndDeletedAtIsNull(UUID organizationId, Pageable pageable);

    boolean existsByIdAndOrganizationIdAndDeletedAtIsNull(UUID id, UUID organizationId);
}
