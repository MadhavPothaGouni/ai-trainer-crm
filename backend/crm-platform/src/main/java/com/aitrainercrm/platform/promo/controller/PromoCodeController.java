package com.aitrainercrm.platform.promo.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.promo.dto.CreatePromoCodeRequest;
import com.aitrainercrm.platform.promo.dto.PromoCodeDto;
import com.aitrainercrm.platform.promo.dto.UpdatePromoCodeRequest;
import com.aitrainercrm.platform.promo.entity.PromoCode;
import com.aitrainercrm.platform.promo.service.PromoCodeService;
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

/** No OWN scope on PROMO_CODE (see PromoCodeService's javadoc) - mirrors LockerController exactly. */
@RestController
@RequestMapping("/api/v1/promo-codes")
@RequiredArgsConstructor
public class PromoCodeController {

    private final PromoCodeService promoCodeService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('PROMO_CODE:READ:TEAM','PROMO_CODE:READ:DEPARTMENT','PROMO_CODE:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<PromoCodeDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<PromoCode> page = promoCodeService.list(principal, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(PromoCodeDto::from).toList()));
    }

    @GetMapping("/{promoCodeId}")
    @PreAuthorize("hasAnyAuthority('PROMO_CODE:READ:TEAM','PROMO_CODE:READ:DEPARTMENT','PROMO_CODE:READ:ORGANIZATION')")
    public ApiResponse<PromoCodeDto> get(@PathVariable UUID promoCodeId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(PromoCodeDto.from(promoCodeService.get(principal, promoCodeId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('PROMO_CODE:CREATE:TEAM','PROMO_CODE:CREATE:DEPARTMENT','PROMO_CODE:CREATE:ORGANIZATION')")
    public ApiResponse<PromoCodeDto> create(@Valid @RequestBody CreatePromoCodeRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(PromoCodeDto.from(promoCodeService.create(principal, request)), "Promo code added");
    }

    @PutMapping("/{promoCodeId}")
    @PreAuthorize("hasAnyAuthority('PROMO_CODE:UPDATE:TEAM','PROMO_CODE:UPDATE:DEPARTMENT','PROMO_CODE:UPDATE:ORGANIZATION')")
    public ApiResponse<PromoCodeDto> update(
            @PathVariable UUID promoCodeId, @Valid @RequestBody UpdatePromoCodeRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(PromoCodeDto.from(promoCodeService.update(principal, promoCodeId, request)), "Promo code updated");
    }

    @DeleteMapping("/{promoCodeId}")
    @PreAuthorize("hasAnyAuthority('PROMO_CODE:DELETE:TEAM','PROMO_CODE:DELETE:DEPARTMENT','PROMO_CODE:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID promoCodeId, @AuthenticationPrincipal UserPrincipal principal) {
        promoCodeService.delete(principal, promoCodeId);
        return ApiResponse.ok(null, "Promo code deleted");
    }
}
