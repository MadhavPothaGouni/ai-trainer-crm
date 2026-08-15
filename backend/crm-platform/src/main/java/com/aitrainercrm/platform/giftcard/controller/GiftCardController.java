package com.aitrainercrm.platform.giftcard.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.giftcard.dto.CreateGiftCardRequest;
import com.aitrainercrm.platform.giftcard.dto.GiftCardDto;
import com.aitrainercrm.platform.giftcard.dto.RedeemGiftCardRequest;
import com.aitrainercrm.platform.giftcard.dto.UpdateGiftCardRequest;
import com.aitrainercrm.platform.giftcard.dto.UpdateGiftCardStatusRequest;
import com.aitrainercrm.platform.giftcard.entity.GiftCard;
import com.aitrainercrm.platform.giftcard.service.GiftCardService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Mirrors LockerAssignmentController's shape, plus a POST .../redeem endpoint - see GiftCardService#redeem's javadoc for why redemption isn't just a status PATCH. */
@RestController
@RequestMapping("/api/v1/gift-cards")
@RequiredArgsConstructor
public class GiftCardController {

    private final GiftCardService giftCardService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('GIFT_CARD:READ:OWN','GIFT_CARD:READ:TEAM','GIFT_CARD:READ:DEPARTMENT','GIFT_CARD:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<GiftCardDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<GiftCard> page = giftCardService.list(principal, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(GiftCardDto::from).toList()));
    }

    @GetMapping("/{giftCardId}")
    @PreAuthorize("hasAnyAuthority('GIFT_CARD:READ:OWN','GIFT_CARD:READ:TEAM','GIFT_CARD:READ:DEPARTMENT','GIFT_CARD:READ:ORGANIZATION')")
    public ApiResponse<GiftCardDto> get(@PathVariable UUID giftCardId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(GiftCardDto.from(giftCardService.get(principal, giftCardId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('GIFT_CARD:CREATE:OWN','GIFT_CARD:CREATE:TEAM','GIFT_CARD:CREATE:DEPARTMENT','GIFT_CARD:CREATE:ORGANIZATION')")
    public ApiResponse<GiftCardDto> create(@Valid @RequestBody CreateGiftCardRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(GiftCardDto.from(giftCardService.create(principal, request)), "Gift card issued");
    }

    @PutMapping("/{giftCardId}")
    @PreAuthorize("hasAnyAuthority('GIFT_CARD:UPDATE:OWN','GIFT_CARD:UPDATE:TEAM','GIFT_CARD:UPDATE:DEPARTMENT','GIFT_CARD:UPDATE:ORGANIZATION')")
    public ApiResponse<GiftCardDto> update(
            @PathVariable UUID giftCardId, @Valid @RequestBody UpdateGiftCardRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(GiftCardDto.from(giftCardService.update(principal, giftCardId, request)), "Gift card updated");
    }

    @PatchMapping("/{giftCardId}/status")
    @PreAuthorize("hasAnyAuthority('GIFT_CARD:UPDATE:OWN','GIFT_CARD:UPDATE:TEAM','GIFT_CARD:UPDATE:DEPARTMENT','GIFT_CARD:UPDATE:ORGANIZATION')")
    public ApiResponse<GiftCardDto> updateStatus(
            @PathVariable UUID giftCardId, @Valid @RequestBody UpdateGiftCardStatusRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(GiftCardDto.from(giftCardService.updateStatus(principal, giftCardId, request.status())), "Status updated");
    }

    @PostMapping("/{giftCardId}/redeem")
    @PreAuthorize("hasAnyAuthority('GIFT_CARD:UPDATE:OWN','GIFT_CARD:UPDATE:TEAM','GIFT_CARD:UPDATE:DEPARTMENT','GIFT_CARD:UPDATE:ORGANIZATION')")
    public ApiResponse<GiftCardDto> redeem(
            @PathVariable UUID giftCardId, @Valid @RequestBody RedeemGiftCardRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(GiftCardDto.from(giftCardService.redeem(principal, giftCardId, request.amount())), "Gift card redeemed");
    }

    @DeleteMapping("/{giftCardId}")
    @PreAuthorize("hasAnyAuthority('GIFT_CARD:DELETE:OWN','GIFT_CARD:DELETE:TEAM','GIFT_CARD:DELETE:DEPARTMENT','GIFT_CARD:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID giftCardId, @AuthenticationPrincipal UserPrincipal principal) {
        giftCardService.delete(principal, giftCardId);
        return ApiResponse.ok(null, "Gift card deleted");
    }
}
