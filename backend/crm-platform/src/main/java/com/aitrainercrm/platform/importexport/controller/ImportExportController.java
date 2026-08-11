package com.aitrainercrm.platform.importexport.controller;

import com.aitrainercrm.platform.common.dto.ApiResponse;
import com.aitrainercrm.platform.common.dto.PageResponse;
import com.aitrainercrm.platform.importexport.dto.ImportJobDto;
import com.aitrainercrm.platform.importexport.dto.ImportRowErrorDto;
import com.aitrainercrm.platform.importexport.entity.ImportJob;
import com.aitrainercrm.platform.importexport.service.ImportExportService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * CSV import/export for Account, Contact, Lead, and Ticket - see {@link ImportExportService}'s
 * javadoc for why this exists as its own module rather than extra methods bolted onto {@code
 * AccountController}/{@code ContactController}/{@code LeadController}/{@code TicketController}:
 * it's genuinely cross-cutting (the import-job history endpoints at the bottom span all four
 * entity types), and keeping it separate means those controllers never need to know {@code
 * MultipartFile} exists.
 *
 * <p>Export endpoints follow the exact convention {@code CampaignController#export} established:
 * a raw {@code ResponseEntity<byte[]>} download, not the usual {@code ApiResponse} envelope. Import
 * endpoints return a normal {@code ApiResponse<ImportJobDto>} - unlike an export, an import result
 * is data a UI needs to render (success/error counts, a per-row error table), not a file.
 */
@RestController
@RequiredArgsConstructor
public class ImportExportController {

    private final ImportExportService importExportService;

    @GetMapping("/api/v1/accounts/export")
    @PreAuthorize("hasAnyAuthority('ACCOUNT:EXPORT:OWN','ACCOUNT:EXPORT:TEAM','ACCOUNT:EXPORT:DEPARTMENT','ACCOUNT:EXPORT:ORGANIZATION')")
    public ResponseEntity<byte[]> exportAccounts(@AuthenticationPrincipal UserPrincipal principal) {
        return csvDownload(importExportService.exportAccounts(principal), "accounts.csv");
    }

    @PostMapping("/api/v1/accounts/import")
    @PreAuthorize("hasAnyAuthority('ACCOUNT:IMPORT:OWN','ACCOUNT:IMPORT:TEAM','ACCOUNT:IMPORT:DEPARTMENT','ACCOUNT:IMPORT:ORGANIZATION')")
    public ApiResponse<ImportJobDto> importAccounts(@RequestParam MultipartFile file, @AuthenticationPrincipal UserPrincipal principal) {
        ImportJob job = importExportService.importAccounts(principal, file);
        return ApiResponse.ok(toDto(job), "Import finished: %d succeeded, %d failed".formatted(job.getSuccessCount(), job.getErrorCount()));
    }

    @GetMapping("/api/v1/contacts/export")
    @PreAuthorize("hasAnyAuthority('CONTACT:EXPORT:OWN','CONTACT:EXPORT:TEAM','CONTACT:EXPORT:DEPARTMENT','CONTACT:EXPORT:ORGANIZATION')")
    public ResponseEntity<byte[]> exportContacts(@AuthenticationPrincipal UserPrincipal principal) {
        return csvDownload(importExportService.exportContacts(principal), "contacts.csv");
    }

    @PostMapping("/api/v1/contacts/import")
    @PreAuthorize("hasAnyAuthority('CONTACT:IMPORT:OWN','CONTACT:IMPORT:TEAM','CONTACT:IMPORT:DEPARTMENT','CONTACT:IMPORT:ORGANIZATION')")
    public ApiResponse<ImportJobDto> importContacts(@RequestParam MultipartFile file, @AuthenticationPrincipal UserPrincipal principal) {
        ImportJob job = importExportService.importContacts(principal, file);
        return ApiResponse.ok(toDto(job), "Import finished: %d succeeded, %d failed".formatted(job.getSuccessCount(), job.getErrorCount()));
    }

    @GetMapping("/api/v1/leads/export")
    @PreAuthorize("hasAnyAuthority('LEAD:EXPORT:OWN','LEAD:EXPORT:TEAM','LEAD:EXPORT:DEPARTMENT','LEAD:EXPORT:ORGANIZATION')")
    public ResponseEntity<byte[]> exportLeads(@AuthenticationPrincipal UserPrincipal principal) {
        return csvDownload(importExportService.exportLeads(principal), "leads.csv");
    }

    @PostMapping("/api/v1/leads/import")
    @PreAuthorize("hasAnyAuthority('LEAD:IMPORT:OWN','LEAD:IMPORT:TEAM','LEAD:IMPORT:DEPARTMENT','LEAD:IMPORT:ORGANIZATION')")
    public ApiResponse<ImportJobDto> importLeads(@RequestParam MultipartFile file, @AuthenticationPrincipal UserPrincipal principal) {
        ImportJob job = importExportService.importLeads(principal, file);
        return ApiResponse.ok(toDto(job), "Import finished: %d succeeded, %d failed".formatted(job.getSuccessCount(), job.getErrorCount()));
    }

    @GetMapping("/api/v1/tickets/export")
    @PreAuthorize("hasAnyAuthority('TICKET:EXPORT:OWN','TICKET:EXPORT:TEAM','TICKET:EXPORT:DEPARTMENT','TICKET:EXPORT:ORGANIZATION')")
    public ResponseEntity<byte[]> exportTickets(@AuthenticationPrincipal UserPrincipal principal) {
        return csvDownload(importExportService.exportTickets(principal), "tickets.csv");
    }

    @PostMapping("/api/v1/tickets/import")
    @PreAuthorize("hasAnyAuthority('TICKET:IMPORT:OWN','TICKET:IMPORT:TEAM','TICKET:IMPORT:DEPARTMENT','TICKET:IMPORT:ORGANIZATION')")
    public ApiResponse<ImportJobDto> importTickets(@RequestParam MultipartFile file, @AuthenticationPrincipal UserPrincipal principal) {
        ImportJob job = importExportService.importTickets(principal, file);
        return ApiResponse.ok(toDto(job), "Import finished: %d succeeded, %d failed".formatted(job.getSuccessCount(), job.getErrorCount()));
    }

    /**
     * History spans all four entity types, so this is gated on holding IMPORT for at least one of
     * them rather than a single resource's authority list - a caller who can only import Leads
     * should still be able to see their own lead-import history, without needing ACCOUNT/CONTACT/
     * TICKET permissions they don't hold.
     */
    @GetMapping("/api/v1/import-jobs")
    @PreAuthorize("hasAnyAuthority("
            + "'ACCOUNT:IMPORT:OWN','ACCOUNT:IMPORT:TEAM','ACCOUNT:IMPORT:DEPARTMENT','ACCOUNT:IMPORT:ORGANIZATION',"
            + "'CONTACT:IMPORT:OWN','CONTACT:IMPORT:TEAM','CONTACT:IMPORT:DEPARTMENT','CONTACT:IMPORT:ORGANIZATION',"
            + "'LEAD:IMPORT:OWN','LEAD:IMPORT:TEAM','LEAD:IMPORT:DEPARTMENT','LEAD:IMPORT:ORGANIZATION',"
            + "'TICKET:IMPORT:OWN','TICKET:IMPORT:TEAM','TICKET:IMPORT:DEPARTMENT','TICKET:IMPORT:ORGANIZATION')")
    public ApiResponse<PageResponse<ImportJobDto>> listJobs(Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        Page<ImportJob> page = importExportService.listJobs(principal, pageable);
        return ApiResponse.ok(PageResponse.from(page, page.getContent().stream().map(j -> ImportJobDto.from(j, List.of())).toList()));
    }

    @GetMapping("/api/v1/import-jobs/{jobId}")
    @PreAuthorize("hasAnyAuthority("
            + "'ACCOUNT:IMPORT:OWN','ACCOUNT:IMPORT:TEAM','ACCOUNT:IMPORT:DEPARTMENT','ACCOUNT:IMPORT:ORGANIZATION',"
            + "'CONTACT:IMPORT:OWN','CONTACT:IMPORT:TEAM','CONTACT:IMPORT:DEPARTMENT','CONTACT:IMPORT:ORGANIZATION',"
            + "'LEAD:IMPORT:OWN','LEAD:IMPORT:TEAM','LEAD:IMPORT:DEPARTMENT','LEAD:IMPORT:ORGANIZATION',"
            + "'TICKET:IMPORT:OWN','TICKET:IMPORT:TEAM','TICKET:IMPORT:DEPARTMENT','TICKET:IMPORT:ORGANIZATION')")
    public ApiResponse<ImportJobDto> getJob(@PathVariable UUID jobId, @AuthenticationPrincipal UserPrincipal principal) {
        ImportJob job = importExportService.getJob(principal, jobId);
        return ApiResponse.ok(toDto(job));
    }

    private ImportJobDto toDto(ImportJob job) {
        var errors = importExportService.getJobErrors(job.getId()).stream().map(ImportRowErrorDto::from).toList();
        return ImportJobDto.from(job, errors);
    }

    private ResponseEntity<byte[]> csvDownload(byte[] csv, String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(csv);
    }
}
