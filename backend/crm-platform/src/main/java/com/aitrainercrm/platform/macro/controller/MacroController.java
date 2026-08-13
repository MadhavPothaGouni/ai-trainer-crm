package com.aitrainercrm.platform.macro.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.macro.dto.ApplyMacroRequest;
import com.aitrainercrm.platform.macro.dto.CreateMacroRequest;
import com.aitrainercrm.platform.macro.dto.MacroDto;
import com.aitrainercrm.platform.macro.dto.UpdateMacroRequest;
import com.aitrainercrm.platform.macro.entity.Macro;
import com.aitrainercrm.platform.macro.service.MacroService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.ticket.dto.TicketDto;
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

/** No OWN scope on MACRO (see MacroService's javadoc) - mirrors CourseController exactly. The apply endpoint is gated on MACRO:READ, not any TICKET authority - see MacroService#apply's javadoc for why the Ticket-side authorization happens inside TicketService instead. */
@RestController
@RequestMapping("/api/v1/macros")
@RequiredArgsConstructor
public class MacroController {

    private final MacroService macroService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('MACRO:READ:TEAM','MACRO:READ:DEPARTMENT','MACRO:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<MacroDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<Macro> page = macroService.list(principal, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(MacroDto::from).toList()));
    }

    @GetMapping("/active")
    @PreAuthorize("hasAnyAuthority('MACRO:READ:TEAM','MACRO:READ:DEPARTMENT','MACRO:READ:ORGANIZATION')")
    public ApiResponse<List<MacroDto>> listActive(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(macroService.listActive(principal).stream().map(MacroDto::from).toList());
    }

    @GetMapping("/{macroId}")
    @PreAuthorize("hasAnyAuthority('MACRO:READ:TEAM','MACRO:READ:DEPARTMENT','MACRO:READ:ORGANIZATION')")
    public ApiResponse<MacroDto> get(@PathVariable UUID macroId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(MacroDto.from(macroService.get(principal, macroId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('MACRO:CREATE:TEAM','MACRO:CREATE:DEPARTMENT','MACRO:CREATE:ORGANIZATION')")
    public ApiResponse<MacroDto> create(@Valid @RequestBody CreateMacroRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(MacroDto.from(macroService.create(principal, request)), "Macro created");
    }

    @PutMapping("/{macroId}")
    @PreAuthorize("hasAnyAuthority('MACRO:UPDATE:TEAM','MACRO:UPDATE:DEPARTMENT','MACRO:UPDATE:ORGANIZATION')")
    public ApiResponse<MacroDto> update(
            @PathVariable UUID macroId, @Valid @RequestBody UpdateMacroRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(MacroDto.from(macroService.update(principal, macroId, request)), "Macro updated");
    }

    @DeleteMapping("/{macroId}")
    @PreAuthorize("hasAnyAuthority('MACRO:DELETE:TEAM','MACRO:DELETE:DEPARTMENT','MACRO:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID macroId, @AuthenticationPrincipal UserPrincipal principal) {
        macroService.delete(principal, macroId);
        return ApiResponse.ok(null, "Macro deleted");
    }

    @PatchMapping("/{macroId}/apply")
    @PreAuthorize("hasAnyAuthority('MACRO:READ:TEAM','MACRO:READ:DEPARTMENT','MACRO:READ:ORGANIZATION')")
    public ApiResponse<TicketDto> apply(
            @PathVariable UUID macroId, @Valid @RequestBody ApplyMacroRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(TicketDto.from(macroService.apply(principal, macroId, request.ticketId())), "Macro applied");
    }
}
