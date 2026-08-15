package com.aitrainercrm.platform.giftcard.repository;

import com.aitrainercrm.platform.giftcard.entity.GiftCard;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GiftCardRepository extends JpaRepository<GiftCard, UUID> {

    @Query("select g from GiftCard g where g.id = :id and g.organizationId = :organizationId and g.deletedAt is null")
    Optional<GiftCard> findActiveByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    Page<GiftCard> findByOrganizationIdAndDeletedAtIsNull(UUID organizationId, Pageable pageable);

    Page<GiftCard> findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(UUID organizationId, Set<UUID> ownerIds, Pageable pageable);
}
