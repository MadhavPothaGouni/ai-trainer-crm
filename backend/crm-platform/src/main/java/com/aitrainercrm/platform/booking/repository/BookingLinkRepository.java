package com.aitrainercrm.platform.booking.repository;

import com.aitrainercrm.platform.booking.entity.BookingLink;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingLinkRepository extends JpaRepository<BookingLink, UUID> {

    @Query("select b from BookingLink b where b.id = :id and b.organizationId = :organizationId and b.deletedAt is null")
    Optional<BookingLink> findActiveByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    Page<BookingLink> findByOrganizationIdAndDeletedAtIsNullOrderByTitleAsc(UUID organizationId, Pageable pageable);

    Page<BookingLink> findByOrganizationIdAndOwnerIdInAndDeletedAtIsNullOrderByTitleAsc(UUID organizationId, Set<UUID> ownerIds, Pageable pageable);

    boolean existsByOrganizationIdAndSlugAndDeletedAtIsNull(UUID organizationId, String slug);

    boolean existsByOrganizationIdAndSlugAndIdNotAndDeletedAtIsNull(UUID organizationId, String slug, UUID id);
}
