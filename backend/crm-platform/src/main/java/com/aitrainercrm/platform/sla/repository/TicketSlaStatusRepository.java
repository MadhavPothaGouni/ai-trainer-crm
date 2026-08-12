package com.aitrainercrm.platform.sla.repository;

import com.aitrainercrm.platform.sla.entity.TicketSlaStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketSlaStatusRepository extends JpaRepository<TicketSlaStatus, UUID> {

    Optional<TicketSlaStatus> findByTicketId(UUID ticketId);
}
