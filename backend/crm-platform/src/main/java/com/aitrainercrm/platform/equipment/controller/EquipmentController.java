package com.aitrainercrm.platform.equipment.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.equipment.dto.CreateEquipmentRequest;
import com.aitrainercrm.platform.equipment.dto.EquipmentDto;
import com.aitrainercrm.platform.equipment.dto.UpdateEquipmentRequest;
import com.aitrainercrm.platform.equipment.entity.Equipment;
import com.aitrainercrm.platform.equipment.service.EquipmentService;
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

/** No OWN scope on EQUIPMENT (see EquipmentService's javadoc) - mirrors ProductController/GroupClassController exactly. */
@RestController
@RequestMapping("/api/v1/equipment")
@RequiredArgsConstructor
public class EquipmentController {

    private final EquipmentService equipmentService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('EQUIPMENT:READ:TEAM','EQUIPMENT:READ:DEPARTMENT','EQUIPMENT:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<EquipmentDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<Equipment> page = equipmentService.list(principal, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(EquipmentDto::from).toList()));
    }

    @GetMapping("/{equipmentId}")
    @PreAuthorize("hasAnyAuthority('EQUIPMENT:READ:TEAM','EQUIPMENT:READ:DEPARTMENT','EQUIPMENT:READ:ORGANIZATION')")
    public ApiResponse<EquipmentDto> get(@PathVariable UUID equipmentId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(EquipmentDto.from(equipmentService.get(principal, equipmentId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('EQUIPMENT:CREATE:TEAM','EQUIPMENT:CREATE:DEPARTMENT','EQUIPMENT:CREATE:ORGANIZATION')")
    public ApiResponse<EquipmentDto> create(@Valid @RequestBody CreateEquipmentRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(EquipmentDto.from(equipmentService.create(principal, request)), "Equipment added");
    }

    @PutMapping("/{equipmentId}")
    @PreAuthorize("hasAnyAuthority('EQUIPMENT:UPDATE:TEAM','EQUIPMENT:UPDATE:DEPARTMENT','EQUIPMENT:UPDATE:ORGANIZATION')")
    public ApiResponse<EquipmentDto> update(
            @PathVariable UUID equipmentId, @Valid @RequestBody UpdateEquipmentRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(EquipmentDto.from(equipmentService.update(principal, equipmentId, request)), "Equipment updated");
    }

    @DeleteMapping("/{equipmentId}")
    @PreAuthorize("hasAnyAuthority('EQUIPMENT:DELETE:TEAM','EQUIPMENT:DELETE:DEPARTMENT','EQUIPMENT:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID equipmentId, @AuthenticationPrincipal UserPrincipal principal) {
        equipmentService.delete(principal, equipmentId);
        return ApiResponse.ok(null, "Equipment deleted");
    }
}
