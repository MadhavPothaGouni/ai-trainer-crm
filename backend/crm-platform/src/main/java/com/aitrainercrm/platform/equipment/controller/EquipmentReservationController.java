package com.aitrainercrm.platform.equipment.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.equipment.dto.CreateEquipmentReservationRequest;
import com.aitrainercrm.platform.equipment.dto.EquipmentReservationDto;
import com.aitrainercrm.platform.equipment.dto.UpdateEquipmentReservationRequest;
import com.aitrainercrm.platform.equipment.dto.UpdateEquipmentReservationStatusRequest;
import com.aitrainercrm.platform.equipment.entity.EquipmentReservation;
import com.aitrainercrm.platform.equipment.service.EquipmentReservationService;
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
@RequestMapping("/api/v1/equipment-reservations")
@RequiredArgsConstructor
public class EquipmentReservationController {

    private final EquipmentReservationService equipmentReservationService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('EQUIPMENT_RESERVATION:READ:OWN','EQUIPMENT_RESERVATION:READ:TEAM','EQUIPMENT_RESERVATION:READ:DEPARTMENT','EQUIPMENT_RESERVATION:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<EquipmentReservationDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<EquipmentReservation> page = equipmentReservationService.list(principal, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(EquipmentReservationDto::from).toList()));
    }

    @GetMapping("/{equipmentReservationId}")
    @PreAuthorize("hasAnyAuthority('EQUIPMENT_RESERVATION:READ:OWN','EQUIPMENT_RESERVATION:READ:TEAM','EQUIPMENT_RESERVATION:READ:DEPARTMENT','EQUIPMENT_RESERVATION:READ:ORGANIZATION')")
    public ApiResponse<EquipmentReservationDto> get(@PathVariable UUID equipmentReservationId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(EquipmentReservationDto.from(equipmentReservationService.get(principal, equipmentReservationId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('EQUIPMENT_RESERVATION:CREATE:OWN','EQUIPMENT_RESERVATION:CREATE:TEAM','EQUIPMENT_RESERVATION:CREATE:DEPARTMENT','EQUIPMENT_RESERVATION:CREATE:ORGANIZATION')")
    public ApiResponse<EquipmentReservationDto> create(
            @Valid @RequestBody CreateEquipmentReservationRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(EquipmentReservationDto.from(equipmentReservationService.create(principal, request)), "Equipment reservation created");
    }

    @PutMapping("/{equipmentReservationId}")
    @PreAuthorize("hasAnyAuthority('EQUIPMENT_RESERVATION:UPDATE:OWN','EQUIPMENT_RESERVATION:UPDATE:TEAM','EQUIPMENT_RESERVATION:UPDATE:DEPARTMENT','EQUIPMENT_RESERVATION:UPDATE:ORGANIZATION')")
    public ApiResponse<EquipmentReservationDto> update(
            @PathVariable UUID equipmentReservationId,
            @Valid @RequestBody UpdateEquipmentReservationRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(
                EquipmentReservationDto.from(equipmentReservationService.update(principal, equipmentReservationId, request)), "Equipment reservation updated");
    }

    @PatchMapping("/{equipmentReservationId}/status")
    @PreAuthorize("hasAnyAuthority('EQUIPMENT_RESERVATION:UPDATE:OWN','EQUIPMENT_RESERVATION:UPDATE:TEAM','EQUIPMENT_RESERVATION:UPDATE:DEPARTMENT','EQUIPMENT_RESERVATION:UPDATE:ORGANIZATION')")
    public ApiResponse<EquipmentReservationDto> updateStatus(
            @PathVariable UUID equipmentReservationId,
            @Valid @RequestBody UpdateEquipmentReservationStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(
                EquipmentReservationDto.from(equipmentReservationService.updateStatus(principal, equipmentReservationId, request.status())),
                "Status updated");
    }

    @DeleteMapping("/{equipmentReservationId}")
    @PreAuthorize("hasAnyAuthority('EQUIPMENT_RESERVATION:DELETE:OWN','EQUIPMENT_RESERVATION:DELETE:TEAM','EQUIPMENT_RESERVATION:DELETE:DEPARTMENT','EQUIPMENT_RESERVATION:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID equipmentReservationId, @AuthenticationPrincipal UserPrincipal principal) {
        equipmentReservationService.delete(principal, equipmentReservationId);
        return ApiResponse.ok(null, "Equipment reservation deleted");
    }
}
