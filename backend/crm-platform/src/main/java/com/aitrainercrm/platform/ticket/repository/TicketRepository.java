package com.aitrainercrm.platform.ticket.repository;

import com.aitrainercrm.platform.ticket.entity.Ticket;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    @Query("select t from Ticket t where t.id = :id and t.organizationId = :organizationId and t.deletedAt is null")
    Optional<Ticket> findActiveByIdAndOrganizationId(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    /** Added for EmailMessageService/CalendarEventService#validateRelatedTo - TICKET is now a valid relatedToType for both, same existence-check shape AccountRepository/ContactRepository/OpportunityRepository/LeadRepository already had. */
    boolean existsByIdAndOrganizationIdAndDeletedAtIsNull(UUID id, UUID organizationId);

    Page<Ticket> findByOrganizationIdAndDeletedAtIsNull(UUID organizationId, Pageable pageable);

    Page<Ticket> findByOrganizationIdAndOwnerIdInAndDeletedAtIsNull(UUID organizationId, Set<UUID> ownerIds, Pageable pageable);

    /** Unpaginated variants for CSV export - see AccountRepository's identical pair for why. */
    List<Ticket> findByOrganizationIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID organizationId);

    List<Ticket> findByOrganizationIdAndOwnerIdInAndDeletedAtIsNullOrderByCreatedAtDesc(UUID organizationId, Set<UUID> ownerIds);

    /** Added for SlaEvaluationService#sweep - the one genuinely cross-organization query in this repository (every other finder here is scoped to a single organizationId). The periodic sweep has no caller/tenant context at all, so it has to ask "every still-open ticket in the whole platform," the same way WebhookDispatchListener's event processing isn't scoped to one org either. */
    List<Ticket> findByStatusInAndDeletedAtIsNull(List<Ticket.Status> statuses);
}
