package com.aitrainercrm.platform.room.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.room.dto.CreateRoomBookingRequest;
import com.aitrainercrm.platform.room.dto.RoomBookingDto;
import com.aitrainercrm.platform.room.dto.UpdateRoomBookingRequest;
import com.aitrainercrm.platform.room.dto.UpdateRoomBookingStatusRequest;
import com.aitrainercrm.platform.room.entity.RoomBooking;
import com.aitrainercrm.platform.room.service.RoomBookingService;
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

/** Mirrors LockerAssignmentController's shape exactly, including the separate PATCH .../status endpoint. */
@RestController
@RequestMapping("/api/v1/room-bookings")
@RequiredArgsConstructor
public class RoomBookingController {

    private final RoomBookingService roomBookingService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROOM_BOOKING:READ:OWN','ROOM_BOOKING:READ:TEAM','ROOM_BOOKING:READ:DEPARTMENT','ROOM_BOOKING:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<RoomBookingDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<RoomBooking> page = roomBookingService.list(principal, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(RoomBookingDto::from).toList()));
    }

    @GetMapping("/{roomBookingId}")
    @PreAuthorize("hasAnyAuthority('ROOM_BOOKING:READ:OWN','ROOM_BOOKING:READ:TEAM','ROOM_BOOKING:READ:DEPARTMENT','ROOM_BOOKING:READ:ORGANIZATION')")
    public ApiResponse<RoomBookingDto> get(@PathVariable UUID roomBookingId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(RoomBookingDto.from(roomBookingService.get(principal, roomBookingId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('ROOM_BOOKING:CREATE:OWN','ROOM_BOOKING:CREATE:TEAM','ROOM_BOOKING:CREATE:DEPARTMENT','ROOM_BOOKING:CREATE:ORGANIZATION')")
    public ApiResponse<RoomBookingDto> create(
            @Valid @RequestBody CreateRoomBookingRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(RoomBookingDto.from(roomBookingService.create(principal, request)), "Room booking created");
    }

    @PutMapping("/{roomBookingId}")
    @PreAuthorize("hasAnyAuthority('ROOM_BOOKING:UPDATE:OWN','ROOM_BOOKING:UPDATE:TEAM','ROOM_BOOKING:UPDATE:DEPARTMENT','ROOM_BOOKING:UPDATE:ORGANIZATION')")
    public ApiResponse<RoomBookingDto> update(
            @PathVariable UUID roomBookingId,
            @Valid @RequestBody UpdateRoomBookingRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(RoomBookingDto.from(roomBookingService.update(principal, roomBookingId, request)), "Room booking updated");
    }

    @PatchMapping("/{roomBookingId}/status")
    @PreAuthorize("hasAnyAuthority('ROOM_BOOKING:UPDATE:OWN','ROOM_BOOKING:UPDATE:TEAM','ROOM_BOOKING:UPDATE:DEPARTMENT','ROOM_BOOKING:UPDATE:ORGANIZATION')")
    public ApiResponse<RoomBookingDto> updateStatus(
            @PathVariable UUID roomBookingId,
            @Valid @RequestBody UpdateRoomBookingStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(
                RoomBookingDto.from(roomBookingService.updateStatus(principal, roomBookingId, request.status())), "Status updated");
    }

    @DeleteMapping("/{roomBookingId}")
    @PreAuthorize("hasAnyAuthority('ROOM_BOOKING:DELETE:OWN','ROOM_BOOKING:DELETE:TEAM','ROOM_BOOKING:DELETE:DEPARTMENT','ROOM_BOOKING:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID roomBookingId, @AuthenticationPrincipal UserPrincipal principal) {
        roomBookingService.delete(principal, roomBookingId);
        return ApiResponse.ok(null, "Room booking deleted");
    }
}
