package com.aitrainercrm.platform.emailtemplate.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.emailtemplate.dto.CreateEmailTemplateRequest;
import com.aitrainercrm.platform.emailtemplate.dto.EmailTemplateDto;
import com.aitrainercrm.platform.emailtemplate.dto.RenderEmailTemplateRequest;
import com.aitrainercrm.platform.emailtemplate.dto.RenderedEmailDto;
import com.aitrainercrm.platform.emailtemplate.dto.UpdateEmailTemplateRequest;
import com.aitrainercrm.platform.emailtemplate.entity.EmailTemplate;
import com.aitrainercrm.platform.emailtemplate.service.EmailTemplateService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** No OWN scope on EMAIL_TEMPLATE (see EmailTemplateService's javadoc) - every @PreAuthorize here
 * only lists TEAM/DEPARTMENT/ORGANIZATION, same shape ProductController uses. {@link #render} is
 * gated on READ, not a permission of its own - merging a template's placeholders is just a way of
 * looking at it. */
@RestController
@RequestMapping("/api/v1/email-templates")
@RequiredArgsConstructor
public class EmailTemplateController {

    private final EmailTemplateService emailTemplateService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('EMAIL_TEMPLATE:READ:TEAM','EMAIL_TEMPLATE:READ:DEPARTMENT','EMAIL_TEMPLATE:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<EmailTemplateDto>> list(
            @RequestParam(required = false) EmailTemplate.Category category, Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal) {
        Page<EmailTemplate> page = emailTemplateService.list(principal, category, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(EmailTemplateDto::from).toList()));
    }

    @GetMapping("/{templateId}")
    @PreAuthorize("hasAnyAuthority('EMAIL_TEMPLATE:READ:TEAM','EMAIL_TEMPLATE:READ:DEPARTMENT','EMAIL_TEMPLATE:READ:ORGANIZATION')")
    public ApiResponse<EmailTemplateDto> get(@PathVariable UUID templateId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(EmailTemplateDto.from(emailTemplateService.get(principal, templateId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('EMAIL_TEMPLATE:CREATE:TEAM','EMAIL_TEMPLATE:CREATE:DEPARTMENT','EMAIL_TEMPLATE:CREATE:ORGANIZATION')")
    public ApiResponse<EmailTemplateDto> create(
            @Valid @RequestBody CreateEmailTemplateRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(EmailTemplateDto.from(emailTemplateService.create(principal, request)), "Email template created");
    }

    @PutMapping("/{templateId}")
    @PreAuthorize("hasAnyAuthority('EMAIL_TEMPLATE:UPDATE:TEAM','EMAIL_TEMPLATE:UPDATE:DEPARTMENT','EMAIL_TEMPLATE:UPDATE:ORGANIZATION')")
    public ApiResponse<EmailTemplateDto> update(
            @PathVariable UUID templateId, @Valid @RequestBody UpdateEmailTemplateRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(EmailTemplateDto.from(emailTemplateService.update(principal, templateId, request)), "Email template updated");
    }

    @DeleteMapping("/{templateId}")
    @PreAuthorize("hasAnyAuthority('EMAIL_TEMPLATE:DELETE:TEAM','EMAIL_TEMPLATE:DELETE:DEPARTMENT','EMAIL_TEMPLATE:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID templateId, @AuthenticationPrincipal UserPrincipal principal) {
        emailTemplateService.delete(principal, templateId);
        return ApiResponse.ok(null, "Email template deleted");
    }

    @PostMapping("/{templateId}/render")
    @PreAuthorize("hasAnyAuthority('EMAIL_TEMPLATE:READ:TEAM','EMAIL_TEMPLATE:READ:DEPARTMENT','EMAIL_TEMPLATE:READ:ORGANIZATION')")
    public ApiResponse<RenderedEmailDto> render(
            @PathVariable UUID templateId, @RequestBody RenderEmailTemplateRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(emailTemplateService.render(principal, templateId, request));
    }
}
