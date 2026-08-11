package com.aitrainercrm.platform.order.repository;

import com.aitrainercrm.platform.order.entity.OrderLineItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderLineItemRepository extends JpaRepository<OrderLineItem, UUID> {

    List<OrderLineItem> findByOrderIdOrderByCreatedAtAsc(UUID orderId);

    Optional<OrderLineItem> findByIdAndOrderId(UUID id, UUID orderId);
}
