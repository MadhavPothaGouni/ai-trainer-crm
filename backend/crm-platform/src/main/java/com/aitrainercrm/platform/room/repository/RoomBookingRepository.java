package com.aitrainercrm.platform.room.repository;

import com.aitrainercrm.platform.room.entity.RoomBooking;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomBookingRepository extends JpaRepository<RoomBooking, UUID> {

    @Query("select b from RoomBooking b where b.id = :id and b.organizationId = :organizationId and b.deletedAt is null")
    Optional<RoomBooking> findActiveByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    Page<RoomBooking> findByOrganizationIdAndDeletedAtIsNull(UUID organizationId, Pageable pageable);

    Page<RoomBooking> findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(UUID organizationId, Set<UUID> ownerIds, Pageable pageable);

    /** Used by {@code RoomBookingService#assertNoOverlap} when creating a new booking (nothing to exclude yet). */
    boolean existsByRoomIdAndStatusAndDeletedAtIsNullAndStartsAtLessThanAndEndsAtGreaterThan(
            UUID roomId, RoomBooking.Status status, Instant startsAtLessThan, Instant endsAtGreaterThan);

    /** Same overlap check as above, but excluding the booking being updated/re-confirmed. */
    boolean existsByRoomIdAndStatusAndDeletedAtIsNullAndIdNotAndStartsAtLessThanAndEndsAtGreaterThan(
            UUID roomId, RoomBooking.Status status, UUID excludeId, Instant startsAtLessThan, Instant endsAtGreaterThan);
}
