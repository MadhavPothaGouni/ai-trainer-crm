package com.aitrainercrm.platform.room.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.room.dto.CreateRoomRequest;
import com.aitrainercrm.platform.room.dto.RoomDto;
import com.aitrainercrm.platform.room.dto.UpdateRoomRequest;
import com.aitrainercrm.platform.room.entity.Room;
import com.aitrainercrm.platform.room.service.RoomService;
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

/** No OWN scope on ROOM (see RoomService's javadoc) - mirrors LockerController exactly. */
@RestController
@RequestMapping("/api/v1/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROOM:READ:TEAM','ROOM:READ:DEPARTMENT','ROOM:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<RoomDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<Room> page = roomService.list(principal, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(RoomDto::from).toList()));
    }

    @GetMapping("/{roomId}")
    @PreAuthorize("hasAnyAuthority('ROOM:READ:TEAM','ROOM:READ:DEPARTMENT','ROOM:READ:ORGANIZATION')")
    public ApiResponse<RoomDto> get(@PathVariable UUID roomId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(RoomDto.from(roomService.get(principal, roomId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('ROOM:CREATE:TEAM','ROOM:CREATE:DEPARTMENT','ROOM:CREATE:ORGANIZATION')")
    public ApiResponse<RoomDto> create(@Valid @RequestBody CreateRoomRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(RoomDto.from(roomService.create(principal, request)), "Room added");
    }

    @PutMapping("/{roomId}")
    @PreAuthorize("hasAnyAuthority('ROOM:UPDATE:TEAM','ROOM:UPDATE:DEPARTMENT','ROOM:UPDATE:ORGANIZATION')")
    public ApiResponse<RoomDto> update(
            @PathVariable UUID roomId, @Valid @RequestBody UpdateRoomRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(RoomDto.from(roomService.update(principal, roomId, request)), "Room updated");
    }

    @DeleteMapping("/{roomId}")
    @PreAuthorize("hasAnyAuthority('ROOM:DELETE:TEAM','ROOM:DELETE:DEPARTMENT','ROOM:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID roomId, @AuthenticationPrincipal UserPrincipal principal) {
        roomService.delete(principal, roomId);
        return ApiResponse.ok(null, "Room deleted");
    }
}
