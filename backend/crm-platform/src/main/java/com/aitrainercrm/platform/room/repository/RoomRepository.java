package com.aitrainercrm.platform.room.repository;

import com.aitrainercrm.platform.room.entity.Room;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomRepository extends JpaRepository<Room, UUID> {

    @Query("select r from Room r where r.id = :id and r.organizationId = :organizationId and r.deletedAt is null")
    Optional<Room> findActiveByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    Page<Room> findByOrganizationIdAndDeletedAtIsNull(UUID organizationId, Pageable pageable);

    boolean existsByIdAndOrganizationIdAndDeletedAtIsNull(UUID id, UUID organizationId);
}
