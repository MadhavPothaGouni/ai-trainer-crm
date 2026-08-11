package com.aitrainercrm.platform.quote.repository;

import com.aitrainercrm.platform.quote.entity.QuoteLineItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuoteLineItemRepository extends JpaRepository<QuoteLineItem, UUID> {

    List<QuoteLineItem> findByQuoteIdOrderByCreatedAtAsc(UUID quoteId);

    Optional<QuoteLineItem> findByIdAndQuoteId(UUID id, UUID quoteId);
}
