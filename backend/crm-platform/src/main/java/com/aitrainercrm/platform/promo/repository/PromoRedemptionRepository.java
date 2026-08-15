package com.aitrainercrm.platform.promo.repository;

import com.aitrainercrm.platform.promo.entity.PromoRedemption;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PromoRedemptionRepository extends JpaRepository<PromoRedemption, UUID> {

    @Query("select r from PromoRedemption r where r.id = :id and r.organizationId = :organizationId and r.deletedAt is null")
    Optional<PromoRedemption> findActiveByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    Page<PromoRedemption> findByOrganizationIdAndDeletedAtIsNull(UUID organizationId, Pageable pageable);

    Page<PromoRedemption> findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(UUID organizationId, Set<UUID> ownerIds, Pageable pageable);

    long countByPromoCodeIdAndDeletedAtIsNull(UUID promoCodeId);
}
