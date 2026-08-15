package com.aitrainercrm.platform.promo.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.promo.dto.CreatePromoRedemptionRequest;
import com.aitrainercrm.platform.promo.dto.PromoRedemptionDto;
import com.aitrainercrm.platform.promo.dto.UpdatePromoRedemptionRequest;
import com.aitrainercrm.platform.promo.entity.PromoRedemption;
import com.aitrainercrm.platform.promo.service.PromoRedemptionService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** No status PATCH endpoint - see PromoRedemption's javadoc for why redemptions have no status lifecycle. */
@RestController
@RequestMapping("/api/v1/promo-redemptions")
@RequiredArgsConstructor
public class PromoRedemptionController {

    private final PromoRedemptionService promoRedemptionService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('PROMO_REDEMPTION:READ:OWN','PROMO_REDEMPTION:READ:TEAM','PROMO_REDEMPTION:READ:DEPARTMENT','PROMO_REDEMPTION:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<PromoRedemptionDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<PromoRedemption> page = promoRedemptionService.list(principal, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(PromoRedemptionDto::from).toList()));
    }

    @GetMapping("/{promoRedemptionId}")
    @PreAuthorize("hasAnyAuthority('PROMO_REDEMPTION:READ:OWN','PROMO_REDEMPTION:READ:TEAM','PROMO_REDEMPTION:READ:DEPARTMENT','PROMO_REDEMPTION:READ:ORGANIZATION')")
    public ApiResponse<PromoRedemptionDto> get(@PathVariable UUID promoRedemptionId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(PromoRedemptionDto.from(promoRedemptionService.get(principal, promoRedemptionId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('PROMO_REDEMPTION:CREATE:OWN','PROMO_REDEMPTION:CREATE:TEAM','PROMO_REDEMPTION:CREATE:DEPARTMENT','PROMO_REDEMPTION:CREATE:ORGANIZATION')")
    public ApiResponse<PromoRedemptionDto> create(
            @Valid @RequestBody CreatePromoRedemptionRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(PromoRedemptionDto.from(promoRedemptionService.create(principal, request)), "Promo redemption recorded");
    }

    @PutMapping("/{promoRedemptionId}")
    @PreAuthorize("hasAnyAuthority('PROMO_REDEMPTION:UPDATE:OWN','PROMO_REDEMPTION:UPDATE:TEAM','PROMO_REDEMPTION:UPDATE:DEPARTMENT','PROMO_REDEMPTION:UPDATE:ORGANIZATION')")
    public ApiResponse<PromoRedemptionDto> update(
            @PathVariable UUID promoRedemptionId,
            @Valid @RequestBody UpdatePromoRedemptionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(PromoRedemptionDto.from(promoRedemptionService.update(principal, promoRedemptionId, request)), "Promo redemption updated");
    }

    @DeleteMapping("/{promoRedemptionId}")
    @PreAuthorize("hasAnyAuthority('PROMO_REDEMPTION:DELETE:OWN','PROMO_REDEMPTION:DELETE:TEAM','PROMO_REDEMPTION:DELETE:DEPARTMENT','PROMO_REDEMPTION:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID promoRedemptionId, @AuthenticationPrincipal UserPrincipal principal) {
        promoRedemptionService.delete(principal, promoRedemptionId);
        return ApiResponse.ok(null, "Promo redemption deleted");
    }
}
