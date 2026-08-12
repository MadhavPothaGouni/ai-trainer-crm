package com.aitrainercrm.platform.territory.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.territory.dto.CreateTerritoryRuleRequest;
import com.aitrainercrm.platform.territory.dto.TerritoryRuleDto;
import com.aitrainercrm.platform.territory.dto.UpdateTerritoryRuleRequest;
import com.aitrainercrm.platform.territory.entity.TerritoryRule;
import com.aitrainercrm.platform.territory.service.TerritoryRuleService;
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

/** Admin config CRUD, entirely gated by a single ORGANIZATION-scope authority per action - same shape as SlaPolicyController/CustomFieldController. The actual auto-assignment behavior these rules drive happens asynchronously in TerritoryAssignmentListener, not through any endpoint here. */
@RestController
@RequestMapping("/api/v1/territory-rules")
@RequiredArgsConstructor
public class TerritoryRuleController {

    private final TerritoryRuleService territoryRuleService;

    @GetMapping
    @PreAuthorize("hasAuthority('TERRITORY_RULE:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<TerritoryRuleDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<TerritoryRule> page = territoryRuleService.list(principal, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(TerritoryRuleDto::from).toList()));
    }

    @GetMapping("/{ruleId}")
    @PreAuthorize("hasAuthority('TERRITORY_RULE:READ:ORGANIZATION')")
    public ApiResponse<TerritoryRuleDto> get(@PathVariable UUID ruleId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(TerritoryRuleDto.from(territoryRuleService.get(principal, ruleId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('TERRITORY_RULE:CREATE:ORGANIZATION')")
    public ApiResponse<TerritoryRuleDto> create(
            @Valid @RequestBody CreateTerritoryRuleRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(TerritoryRuleDto.from(territoryRuleService.create(principal, request)), "Territory rule created");
    }

    @PutMapping("/{ruleId}")
    @PreAuthorize("hasAuthority('TERRITORY_RULE:UPDATE:ORGANIZATION')")
    public ApiResponse<TerritoryRuleDto> update(
            @PathVariable UUID ruleId, @Valid @RequestBody UpdateTerritoryRuleRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(TerritoryRuleDto.from(territoryRuleService.update(principal, ruleId, request)), "Territory rule updated");
    }

    @DeleteMapping("/{ruleId}")
    @PreAuthorize("hasAuthority('TERRITORY_RULE:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID ruleId, @AuthenticationPrincipal UserPrincipal principal) {
        territoryRuleService.delete(principal, ruleId);
        return ApiResponse.ok(null, "Territory rule deleted");
    }
}
