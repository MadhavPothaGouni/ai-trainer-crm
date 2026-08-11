package com.aitrainercrm.platform.calendar.repository;

import com.aitrainercrm.platform.calendar.entity.CalendarEvent;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, UUID> {

    @Query("select c from CalendarEvent c where c.id = :id and c.organizationId = :organizationId and c.deletedAt is null")
    Optional<CalendarEvent> findActiveByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    Page<CalendarEvent> findByOrganizationIdAndDeletedAtIsNullOrderByStartAtAsc(UUID organizationId, Pageable pageable);

    Page<CalendarEvent> findByOrganizationIdAndOwnerIdInAndDeletedAtIsNullOrderByStartAtAsc(
            UUID organizationId, Set<UUID> ownerIds, Pageable pageable);

    Page<CalendarEvent> findByOrganizationIdAndRelatedToTypeAndRelatedToIdAndDeletedAtIsNullOrderByStartAtAsc(
            UUID organizationId, CalendarEvent.RelatedToType relatedToType, UUID relatedToId, Pageable pageable);

    Page<CalendarEvent> findByOrganizationIdAndOwnerIdInAndRelatedToTypeAndRelatedToIdAndDeletedAtIsNullOrderByStartAtAsc(
            UUID organizationId, Set<UUID> ownerIds, CalendarEvent.RelatedToType relatedToType, UUID relatedToId, Pageable pageable);

    /** Unpaginated variant for CSV export - see AccountRepository's identical pair for why. */
    List<CalendarEvent> findByOrganizationIdAndDeletedAtIsNullOrderByStartAtAsc(UUID organizationId);

    List<CalendarEvent> findByOrganizationIdAndOwnerIdInAndDeletedAtIsNullOrderByStartAtAsc(UUID organizationId, Set<UUID> ownerIds);
}
