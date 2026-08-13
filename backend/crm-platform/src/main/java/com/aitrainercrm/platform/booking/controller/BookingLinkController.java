package com.aitrainercrm.platform.booking.controller;

import com.aitrainercrm.platform.booking.dto.BookSlotRequest;
import com.aitrainercrm.platform.booking.dto.BookingLinkDto;
import com.aitrainercrm.platform.booking.dto.BookingSlotDto;
import com.aitrainercrm.platform.booking.dto.CreateBookingLinkRequest;
import com.aitrainercrm.platform.booking.dto.CreateBookingSlotRequest;
import com.aitrainercrm.platform.booking.dto.UpdateBookingLinkRequest;
import com.aitrainercrm.platform.booking.entity.BookingLink;
import com.aitrainercrm.platform.booking.service.BookingLinkService;
import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
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

/** Mirrors TicketController's shape - see BookingLinkService's javadoc for the OWN/TEAM/DEPARTMENT/ORGANIZATION reasoning and the book/cancel cross-module notes. */
@RestController
@RequestMapping("/api/v1/booking-links")
@RequiredArgsConstructor
public class BookingLinkController {

    private final BookingLinkService bookingLinkService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('BOOKING_LINK:READ:OWN','BOOKING_LINK:READ:TEAM','BOOKING_LINK:READ:DEPARTMENT','BOOKING_LINK:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<BookingLinkDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<BookingLink> page = bookingLinkService.list(principal, pageable);
        return ApiResponse.ok(PageResponse.from(
                page, page.getContent().stream().map(link -> BookingLinkDto.from(link, bookingLinkService.getSlots(principal, link.getId()))).toList()));
    }

    @GetMapping("/{bookingLinkId}")
    @PreAuthorize("hasAnyAuthority('BOOKING_LINK:READ:OWN','BOOKING_LINK:READ:TEAM','BOOKING_LINK:READ:DEPARTMENT','BOOKING_LINK:READ:ORGANIZATION')")
    public ApiResponse<BookingLinkDto> get(@PathVariable UUID bookingLinkId, @AuthenticationPrincipal UserPrincipal principal) {
        BookingLink link = bookingLinkService.get(principal, bookingLinkId);
        return ApiResponse.ok(BookingLinkDto.from(link, bookingLinkService.getSlots(principal, bookingLinkId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('BOOKING_LINK:CREATE:OWN','BOOKING_LINK:CREATE:TEAM','BOOKING_LINK:CREATE:DEPARTMENT','BOOKING_LINK:CREATE:ORGANIZATION')")
    public ApiResponse<BookingLinkDto> create(@Valid @RequestBody CreateBookingLinkRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        BookingLink link = bookingLinkService.create(principal, request);
        return ApiResponse.ok(BookingLinkDto.from(link, List.of()), "Booking link created");
    }

    @PutMapping("/{bookingLinkId}")
    @PreAuthorize("hasAnyAuthority('BOOKING_LINK:UPDATE:OWN','BOOKING_LINK:UPDATE:TEAM','BOOKING_LINK:UPDATE:DEPARTMENT','BOOKING_LINK:UPDATE:ORGANIZATION')")
    public ApiResponse<BookingLinkDto> update(
            @PathVariable UUID bookingLinkId, @Valid @RequestBody UpdateBookingLinkRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        BookingLink link = bookingLinkService.update(principal, bookingLinkId, request);
        return ApiResponse.ok(BookingLinkDto.from(link, bookingLinkService.getSlots(principal, bookingLinkId)), "Booking link updated");
    }

    @DeleteMapping("/{bookingLinkId}")
    @PreAuthorize("hasAnyAuthority('BOOKING_LINK:DELETE:OWN','BOOKING_LINK:DELETE:TEAM','BOOKING_LINK:DELETE:DEPARTMENT','BOOKING_LINK:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID bookingLinkId, @AuthenticationPrincipal UserPrincipal principal) {
        bookingLinkService.delete(principal, bookingLinkId);
        return ApiResponse.ok(null, "Booking link deleted");
    }

    @PostMapping("/{bookingLinkId}/slots")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('BOOKING_LINK:UPDATE:OWN','BOOKING_LINK:UPDATE:TEAM','BOOKING_LINK:UPDATE:DEPARTMENT','BOOKING_LINK:UPDATE:ORGANIZATION')")
    public ApiResponse<BookingSlotDto> addSlot(
            @PathVariable UUID bookingLinkId, @Valid @RequestBody CreateBookingSlotRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(BookingSlotDto.from(bookingLinkService.addSlot(principal, bookingLinkId, request)), "Slot added");
    }

    @DeleteMapping("/{bookingLinkId}/slots/{slotId}")
    @PreAuthorize("hasAnyAuthority('BOOKING_LINK:UPDATE:OWN','BOOKING_LINK:UPDATE:TEAM','BOOKING_LINK:UPDATE:DEPARTMENT','BOOKING_LINK:UPDATE:ORGANIZATION')")
    public ApiResponse<Void> removeSlot(
            @PathVariable UUID bookingLinkId, @PathVariable UUID slotId, @AuthenticationPrincipal UserPrincipal principal) {
        bookingLinkService.removeSlot(principal, bookingLinkId, slotId);
        return ApiResponse.ok(null, "Slot removed");
    }

    @PatchMapping("/{bookingLinkId}/slots/{slotId}/book")
    @PreAuthorize("hasAnyAuthority('BOOKING_LINK:UPDATE:OWN','BOOKING_LINK:UPDATE:TEAM','BOOKING_LINK:UPDATE:DEPARTMENT','BOOKING_LINK:UPDATE:ORGANIZATION')")
    public ApiResponse<BookingSlotDto> book(
            @PathVariable UUID bookingLinkId, @PathVariable UUID slotId, @Valid @RequestBody BookSlotRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(BookingSlotDto.from(bookingLinkService.book(principal, bookingLinkId, slotId, request)), "Slot booked");
    }

    @PatchMapping("/{bookingLinkId}/slots/{slotId}/cancel")
    @PreAuthorize("hasAnyAuthority('BOOKING_LINK:UPDATE:OWN','BOOKING_LINK:UPDATE:TEAM','BOOKING_LINK:UPDATE:DEPARTMENT','BOOKING_LINK:UPDATE:ORGANIZATION')")
    public ApiResponse<BookingSlotDto> cancel(
            @PathVariable UUID bookingLinkId, @PathVariable UUID slotId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(BookingSlotDto.from(bookingLinkService.cancel(principal, bookingLinkId, slotId)), "Slot cancelled");
    }
}
