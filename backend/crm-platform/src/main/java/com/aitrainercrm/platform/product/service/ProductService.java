package com.aitrainercrm.platform.product.service;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.product.dto.CreateProductRequest;
import com.aitrainercrm.platform.product.dto.UpdateProductRequest;
import com.aitrainercrm.platform.product.entity.Product;
import com.aitrainercrm.platform.product.repository.ProductRepository;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The product catalog. No {@link com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService}
 * calls here, deliberately - unlike every CRM entity module, Product has no
 * {@code ownerId} to filter by (see the entity's javadoc), so the
 * controller's {@code @PreAuthorize} (any of TEAM/DEPARTMENT/ORGANIZATION)
 * is the whole authorization story: holding any one of those three grants
 * the action against every product in the org.
 */
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<Product> list(UserPrincipal principal, Pageable pageable) {
        return productRepository.findByOrganizationIdAndDeletedAtIsNull(principal.getOrganizationId(), pageable);
    }

    @Transactional(readOnly = true)
    public Product get(UserPrincipal principal, UUID productId) {
        return findOrThrow(principal.getOrganizationId(), productId);
    }

    @Transactional
    public Product create(UserPrincipal principal, CreateProductRequest request) {
        Product product = new Product(principal.getOrganizationId(), request.name());
        applyFields(product, request.sku(), request.description(), request.unitPrice(), request.currency());
        productRepository.save(product);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "Product", product.getId()));
        return product;
    }

    @Transactional
    public Product update(UserPrincipal principal, UUID productId, UpdateProductRequest request) {
        Product product = findOrThrow(principal.getOrganizationId(), productId);
        product.setName(request.name());
        product.setActive(request.active());
        applyFields(product, request.sku(), request.description(), request.unitPrice(), request.currency());
        productRepository.save(product);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "Product", product.getId()));
        return product;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID productId) {
        Product product = findOrThrow(principal.getOrganizationId(), productId);
        product.setDeletedAt(Instant.now());
        productRepository.save(product);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "Product", productId));
    }

    private Product findOrThrow(UUID organizationId, UUID productId) {
        return productRepository.findActiveByIdAndOrganizationId(productId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
    }

    private void applyFields(Product product, String sku, String description, BigDecimal unitPrice, String currency) {
        product.setSku(sku);
        product.setDescription(description);
        product.setUnitPrice(unitPrice);
        product.setCurrency(currency);
    }
}
