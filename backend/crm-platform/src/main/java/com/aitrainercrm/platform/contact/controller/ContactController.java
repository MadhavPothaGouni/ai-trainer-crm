package com.aitrainercrm.platform.contact.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.AssignOwnerRequest;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.contact.dto.ContactDto;
import com.aitrainercrm.platform.contact.dto.CreateContactRequest;
import com.aitrainercrm.platform.contact.dto.UpdateContactRequest;
import com.aitrainercrm.platform.contact.entity.Contact;
import com.aitrainercrm.platform.contact.service.ContactService;
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

@RestController
@RequestMapping("/api/v1/contacts")
@RequiredArgsConstructor
public class ContactController {

    private final ContactService contactService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('CONTACT:READ:OWN','CONTACT:READ:TEAM','CONTACT:READ:DEPARTMENT','CONTACT:READ:ORGANIZATION')")
    public ApiResponse<PageResponse<ContactDto>> list(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<Contact> page = contactService.list(principal, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(ContactDto::from).toList()));
    }

    @GetMapping("/{contactId}")
    @PreAuthorize("hasAnyAuthority('CONTACT:READ:OWN','CONTACT:READ:TEAM','CONTACT:READ:DEPARTMENT','CONTACT:READ:ORGANIZATION')")
    public ApiResponse<ContactDto> get(@PathVariable UUID contactId, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(ContactDto.from(contactService.get(principal, contactId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('CONTACT:CREATE:OWN','CONTACT:CREATE:TEAM','CONTACT:CREATE:DEPARTMENT','CONTACT:CREATE:ORGANIZATION')")
    public ApiResponse<ContactDto> create(@Valid @RequestBody CreateContactRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(ContactDto.from(contactService.create(principal, request)), "Contact created");
    }

    @PutMapping("/{contactId}")
    @PreAuthorize("hasAnyAuthority('CONTACT:UPDATE:OWN','CONTACT:UPDATE:TEAM','CONTACT:UPDATE:DEPARTMENT','CONTACT:UPDATE:ORGANIZATION')")
    public ApiResponse<ContactDto> update(
            @PathVariable UUID contactId, @Valid @RequestBody UpdateContactRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(ContactDto.from(contactService.update(principal, contactId, request)), "Contact updated");
    }

    @DeleteMapping("/{contactId}")
    @PreAuthorize("hasAnyAuthority('CONTACT:DELETE:OWN','CONTACT:DELETE:TEAM','CONTACT:DELETE:DEPARTMENT','CONTACT:DELETE:ORGANIZATION')")
    public ApiResponse<Void> delete(@PathVariable UUID contactId, @AuthenticationPrincipal UserPrincipal principal) {
        contactService.delete(principal, contactId);
        return ApiResponse.ok(null, "Contact deleted");
    }

    @PatchMapping("/{contactId}/owner")
    @PreAuthorize("hasAnyAuthority('CONTACT:ASSIGN:OWN','CONTACT:ASSIGN:TEAM','CONTACT:ASSIGN:DEPARTMENT','CONTACT:ASSIGN:ORGANIZATION')")
    public ApiResponse<ContactDto> assignOwner(
            @PathVariable UUID contactId, @Valid @RequestBody AssignOwnerRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(ContactDto.from(contactService.assignOwner(principal, contactId, request.ownerId())), "Owner updated");
    }
}
