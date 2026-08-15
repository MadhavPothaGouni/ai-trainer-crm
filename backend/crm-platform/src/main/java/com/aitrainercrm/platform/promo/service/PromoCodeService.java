package com.aitrainercrm.platform.promo.service;

import com.aitrainercrm.platform.audit.event.CrmAuditEvents;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.promo.dto.CreatePromoCodeRequest;
import com.aitrainercrm.platform.promo.dto.UpdatePromoCodeRequest;
import com.aitrainercrm.platform.promo.entity.PromoCode;
import com.aitrainercrm.platform.promo.repository.PromoCodeRepository;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The promo code catalog. Exactly {@link com.aitrainercrm.platform.locker.service.LockerService}'s
 * shape - no {@link com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService}
 * calls here, since promo codes have no {@code ownerId} (see {@link PromoCode}'s javadoc); the
 * controller's {@code @PreAuthorize} (any of TEAM/DEPARTMENT/ORGANIZATION) is the whole
 * authorization story. {@link #findOrThrow} is package-private so {@code PromoRedemptionService}
 * can reuse it when validating a new redemption's parent code.
 */
@Service
@RequiredArgsConstructor
public class PromoCodeService {

    private final PromoCodeRepository promoCodeRepository;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<PromoCode> list(UserPrincipal principal, Pageable pageable) {
        return promoCodeRepository.findByOrganizationIdAndDeletedAtIsNull(principal.getOrganizationId(), pageable);
    }

    @Transactional(readOnly = true)
    public PromoCode get(UserPrincipal principal, UUID promoCodeId) {
        return findOrThrow(principal.getOrganizationId(), promoCodeId);
    }

    @Transactional
    public PromoCode create(UserPrincipal principal, CreatePromoCodeRequest request) {
        PromoCode promoCode = new PromoCode(principal.getOrganizationId(), request.code().trim().toUpperCase(), request.discountValue());
        promoCode.setDescription(request.description());
        promoCode.setDiscountType(request.discountType());
        promoCode.setMaxRedemptions(request.maxRedemptions());
        promoCode.setExpiresAt(request.expiresAt());
        promoCode.setNotes(request.notes());
        promoCodeRepository.save(promoCode);

        events.publishEvent(new CrmAuditEvents.RecordCreated(principal.getId(), principal.getOrganizationId(), "PromoCode", promoCode.getId()));
        return promoCode;
    }

    @Transactional
    public PromoCode update(UserPrincipal principal, UUID promoCodeId, UpdatePromoCodeRequest request) {
        PromoCode promoCode = findOrThrow(principal.getOrganizationId(), promoCodeId);
        promoCode.setCode(request.code().trim().toUpperCase());
        promoCode.setDescription(request.description());
        promoCode.setDiscountType(request.discountType());
        promoCode.setDiscountValue(request.discountValue());
        promoCode.setMaxRedemptions(request.maxRedemptions());
        promoCode.setActive(request.active());
        promoCode.setExpiresAt(request.expiresAt());
        promoCode.setNotes(request.notes());
        promoCodeRepository.save(promoCode);

        events.publishEvent(new CrmAuditEvents.RecordUpdated(principal.getId(), principal.getOrganizationId(), "PromoCode", promoCode.getId()));
        return promoCode;
    }

    @Transactional
    public void delete(UserPrincipal principal, UUID promoCodeId) {
        PromoCode promoCode = findOrThrow(principal.getOrganizationId(), promoCodeId);
        promoCode.setDeletedAt(Instant.now());
        promoCodeRepository.save(promoCode);

        events.publishEvent(new CrmAuditEvents.RecordDeleted(principal.getId(), principal.getOrganizationId(), "PromoCode", promoCodeId));
    }

    PromoCode findOrThrow(UUID organizationId, UUID promoCodeId) {
        return promoCodeRepository.findActiveByIdAndOrganizationId(promoCodeId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("PromoCode", promoCodeId));
    }
}
