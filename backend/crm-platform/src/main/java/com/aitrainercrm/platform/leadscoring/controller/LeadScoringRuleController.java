package com.aitrainercrm.platform.leadscoring.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.leadscoring.dto.CreateLeadScoringRuleRequest;
import com.aitrainercrm.platform.leadscoring.dto.LeadScoringRuleDto;
import com.aitrainercrm.platform.leadscoring.dto.UpdateLeadScoringRuleRequest;
import com.aitrainercrm.platform.leadscoring.entity.LeadScoringRule;
import com.aitrainercrm.platform.leadscoring.service.LeadScoringRuleService;
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

/** Admin config CRUD, entirely gated by a single ORGANIZATION-scope authority per action - same shape as TerritoryRuleController/SlaPolicyController. The actual scoring these rules drive happens asynchronously in LeadScoringEngine, not through any endpoint here. */
@RestController
@RequestMapping("/api/v1/lead-scoring-rules")
@RequiredArgsConstructor
public class LeadScoringRuleController {

    private final LeadScoringRuleService leadScoringRuleService;

    @GetMapping
    @PreAuthorize("hasAuthority('LEAD_SCORING_RULE:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<LeadScoringRuleDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<LeadScoringRule> page = leadScoringRuleService.list(principal, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(LeadScoringRuleDto::from).toList()));
    }

    @GetMapping("/{ruleId}")
    @PreAuthorize("hasAuthority('LEAD_SCORING_RULE:READ:ORGANIZATION')")
    public ApiResponse<LeadScoringRuleDto> get(@PathVariable UUID ruleId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(LeadScoringRuleDto.from(leadScoringRuleService.get(principal, ruleId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('LEAD_SCORING_RULE:CREATE:ORGANIZATION')")
    public ApiResponse<LeadScoringRuleDto> create(
            @Valid @RequestBody CreateLeadScoringRuleRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(LeadScoringRuleDto.from(leadScoringRuleService.create(principal, request)), "Lead scoring rule created");
    }

    @PutMapping("/{ruleId}")
    @PreAuthorize("hasAuthority('LEAD_SCORING_RULE:UPDATE:ORGANIZATION')")
    public ApiResponse<LeadScoringRuleDto> update(
            @PathVariable UUID ruleId, @Valid @RequestBody UpdateLeadScoringRuleRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(LeadScoringRuleDto.from(leadScoringRuleService.update(principal, ruleId, request)), "Lead scoring rule updated");
    }

    @DeleteMapping("/{ruleId}")
    @PreAuthorize("hasAuthority('LEAD_SCORING_RULE:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID ruleId, @AuthenticationPrincipal UserPrincipal principal) {
        leadScoringRuleService.delete(principal, ruleId);
        return ApiResponse.ok(null, "Lead scoring rule deleted");
    }
}
