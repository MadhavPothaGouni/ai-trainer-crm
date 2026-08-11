package com.aitrainercrm.platform.invoice.repository;

import com.aitrainercrm.platform.invoice.entity.InvoiceLineItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceLineItemRepository extends JpaRepository<InvoiceLineItem, UUID> {

    List<InvoiceLineItem> findByInvoiceIdOrderByCreatedAtAsc(UUID invoiceId);

    Optional<InvoiceLineItem> findByIdAndInvoiceId(UUID id, UUID invoiceId);
}
