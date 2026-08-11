package com.aitrainercrm.platform.importexport.service;

import com.aitrainercrm.platform.account.dto.CreateAccountRequest;
import com.aitrainercrm.platform.account.entity.Account;
import com.aitrainercrm.platform.account.repository.AccountRepository;
import com.aitrainercrm.platform.account.service.AccountService;
import com.aitrainercrm.platform.common.exception.BusinessException;
import com.aitrainercrm.platform.common.exception.ResourceNotFoundException;
import com.aitrainercrm.platform.common.util.CsvParser;
import com.aitrainercrm.platform.common.util.CsvWriter;
import com.aitrainercrm.platform.contact.dto.CreateContactRequest;
import com.aitrainercrm.platform.contact.entity.Contact;
import com.aitrainercrm.platform.contact.repository.ContactRepository;
import com.aitrainercrm.platform.contact.service.ContactService;
import com.aitrainercrm.platform.importexport.entity.ImportJob;
import com.aitrainercrm.platform.importexport.entity.ImportRowError;
import com.aitrainercrm.platform.importexport.repository.ImportJobRepository;
import com.aitrainercrm.platform.importexport.repository.ImportRowErrorRepository;
import com.aitrainercrm.platform.lead.dto.CreateLeadRequest;
import com.aitrainercrm.platform.lead.entity.Lead;
import com.aitrainercrm.platform.lead.repository.LeadRepository;
import com.aitrainercrm.platform.lead.service.LeadService;
import com.aitrainercrm.platform.role.entity.Permission;
import com.aitrainercrm.platform.security.authorization.ScopeAuthorizationService;
import com.aitrainercrm.platform.security.userdetails.UserPrincipal;
import com.aitrainercrm.platform.ticket.dto.CreateTicketRequest;
import com.aitrainercrm.platform.ticket.entity.Ticket;
import com.aitrainercrm.platform.ticket.repository.TicketRepository;
import com.aitrainercrm.platform.ticket.service.TicketService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Backs the {@code ACCOUNT:EXPORT}/{@code ACCOUNT:IMPORT}, {@code CONTACT:EXPORT}/{@code
 * CONTACT:IMPORT}, {@code LEAD:EXPORT}/{@code LEAD:IMPORT}, and {@code TICKET:EXPORT}/{@code
 * TICKET:IMPORT} permissions - all eight were seeded in V2 alongside every core CRM resource's
 * CRUD/ASSIGN actions, but nothing in the codebase ever implemented IMPORT, and EXPORT only
 * existed for Campaign/Knowledge Article until now (see {@code CampaignService#exportCsv}'s
 * javadoc). TICKET support was added once the {@code ticket} module itself existed (see V14's
 * migration comment) - this class's shape didn't need to change at all, just one more headers
 * constant, one more export/import method pair, and one more row-builder, following the exact
 * pattern the first three entities already established. See V13's migration comment for the full
 * picture.
 *
 * <p><b>Export</b> reuses each entity's own EXPORT scope exactly the way {@code AccountService
 * #list} uses READ - {@link ScopeAuthorizationService#visibleOwnerIds} decides which rows are in
 * the file, so EXPORT is deliberately a distinct, separately-grantable permission from READ (a
 * role can view accounts one at a time without being allowed to bulk-export them), same reasoning
 * {@code CampaignController#export}'s javadoc gives for CAMPAIGN.
 *
 * <p><b>Import</b> always creates rows owned by the importing user - every {@code Create*Request}
 * built from a CSV row passes {@code ownerId = null}, which each entity service's own {@code
 * resolveOwner} already resolves to "the caller" with no further scope check (see {@code
 * AccountService#resolveOwner}'s javadoc: assigning to yourself is always allowed regardless of
 * how broad a scope you hold). That means holding IMPORT at <em>any</em> scope - OWN included - is
 * sufficient to bulk-import; there's no meaningful "import on someone else's behalf" case the way
 * there is for CREATE's {@code ownerId} field, since a CSV row has no natural owner other than
 * whoever uploaded it.
 *
 * <p><b>Why {@link #runImport} is deliberately not {@code @Transactional}:</b> each row is handed
 * to the target entity's own {@code create()} method, which is independently {@code
 * @Transactional}. If this method carried {@code @Transactional} too, every row's {@code create()}
 * call would join that same physical transaction (Spring's default propagation is REQUIRED) - and
 * the moment any one row threw (a bad UUID, a duplicate-ish validation failure, whatever), Spring's
 * transaction interceptor would mark the <em>whole</em> transaction rollback-only before the
 * exception ever reached this method's catch block. Catching it wouldn't un-mark it: every row
 * processed so far, successes included, would silently vanish at commit time with an
 * {@code UnexpectedRollbackException} - exactly the kind of bug that's invisible until a real
 * import batch has one bad row in it. Leaving this method un-annotated lets each row commit (or
 * roll back) independently, which is also the behavior a "partial success with a per-row error
 * report" feature is supposed to have in the first place.
 */
@Service
@RequiredArgsConstructor
public class ImportExportService {

    private static final List<String> ACCOUNT_HEADERS = List.of(
            "Name", "Industry", "Website", "Phone", "Billing Street", "Billing City", "Billing State",
            "Billing Postal Code", "Billing Country", "Annual Revenue", "Employee Count", "Description");
    private static final List<String> ACCOUNT_REQUIRED = List.of("Name");

    private static final List<String> CONTACT_HEADERS =
            List.of("First Name", "Last Name", "Email", "Phone", "Title", "Description", "Account Id");
    private static final List<String> CONTACT_REQUIRED = List.of("First Name", "Last Name");

    private static final List<String> LEAD_HEADERS =
            List.of("First Name", "Last Name", "Email", "Phone", "Company Name", "Title", "Source", "Description");
    private static final List<String> LEAD_REQUIRED = List.of("First Name", "Last Name", "Source");

    private static final List<String> TICKET_HEADERS = List.of("Subject", "Description", "Priority", "Account Id", "Contact Id");
    private static final List<String> TICKET_REQUIRED = List.of("Subject");

    private final AccountService accountService;
    private final ContactService contactService;
    private final LeadService leadService;
    private final TicketService ticketService;
    private final AccountRepository accountRepository;
    private final ContactRepository contactRepository;
    private final LeadRepository leadRepository;
    private final TicketRepository ticketRepository;
    private final ImportJobRepository importJobRepository;
    private final ImportRowErrorRepository importRowErrorRepository;
    private final ScopeAuthorizationService scopeAuthorizationService;
    private final Validator validator;

    // ---- Export ----

    @Transactional(readOnly = true)
    public byte[] exportAccounts(UserPrincipal principal) {
        Optional<Set<UUID>> visibleOwnerIds =
                scopeAuthorizationService.visibleOwnerIds(principal, Permission.Resource.ACCOUNT, Permission.Action.EXPORT);
        List<Account> accounts = visibleOwnerIds
                .map(ids -> accountRepository.findByOrganizationIdAndOwnerIdInAndDeletedAtIsNullOrderByCreatedAtDesc(principal.getOrganizationId(), ids))
                .orElseGet(() -> accountRepository.findByOrganizationIdAndDeletedAtIsNullOrderByCreatedAtDesc(principal.getOrganizationId()));

        CsvWriter csv = new CsvWriter().row(ACCOUNT_HEADERS);
        for (Account a : accounts) {
            csv.row(
                    a.getName(), a.getIndustry(), a.getWebsite(), a.getPhone(), a.getBillingStreet(), a.getBillingCity(),
                    a.getBillingState(), a.getBillingPostalCode(), a.getBillingCountry(), a.getAnnualRevenue(), a.getEmployeeCount(),
                    a.getDescription());
        }
        return csv.toBytes();
    }

    @Transactional(readOnly = true)
    public byte[] exportContacts(UserPrincipal principal) {
        Optional<Set<UUID>> visibleOwnerIds =
                scopeAuthorizationService.visibleOwnerIds(principal, Permission.Resource.CONTACT, Permission.Action.EXPORT);
        List<Contact> contacts = visibleOwnerIds
                .map(ids -> contactRepository.findByOrganizationIdAndOwnerIdInAndDeletedAtIsNullOrderByCreatedAtDesc(principal.getOrganizationId(), ids))
                .orElseGet(() -> contactRepository.findByOrganizationIdAndDeletedAtIsNullOrderByCreatedAtDesc(principal.getOrganizationId()));

        CsvWriter csv = new CsvWriter().row(CONTACT_HEADERS);
        for (Contact c : contacts) {
            csv.row(c.getFirstName(), c.getLastName(), c.getEmail(), c.getPhone(), c.getTitle(), c.getDescription(), c.getAccountId());
        }
        return csv.toBytes();
    }

    @Transactional(readOnly = true)
    public byte[] exportLeads(UserPrincipal principal) {
        Optional<Set<UUID>> visibleOwnerIds =
                scopeAuthorizationService.visibleOwnerIds(principal, Permission.Resource.LEAD, Permission.Action.EXPORT);
        List<Lead> leads = visibleOwnerIds
                .map(ids -> leadRepository.findByOrganizationIdAndOwnerIdInAndDeletedAtIsNullOrderByCreatedAtDesc(principal.getOrganizationId(), ids))
                .orElseGet(() -> leadRepository.findByOrganizationIdAndDeletedAtIsNullOrderByCreatedAtDesc(principal.getOrganizationId()));

        CsvWriter csv = new CsvWriter().row(LEAD_HEADERS);
        for (Lead l : leads) {
            csv.row(l.getFirstName(), l.getLastName(), l.getEmail(), l.getPhone(), l.getCompanyName(), l.getTitle(), l.getSource(), l.getDescription());
        }
        return csv.toBytes();
    }

    @Transactional(readOnly = true)
    public byte[] exportTickets(UserPrincipal principal) {
        Optional<Set<UUID>> visibleOwnerIds =
                scopeAuthorizationService.visibleOwnerIds(principal, Permission.Resource.TICKET, Permission.Action.EXPORT);
        List<Ticket> tickets = visibleOwnerIds
                .map(ids -> ticketRepository.findByOrganizationIdAndOwnerIdInAndDeletedAtIsNullOrderByCreatedAtDesc(principal.getOrganizationId(), ids))
                .orElseGet(() -> ticketRepository.findByOrganizationIdAndDeletedAtIsNullOrderByCreatedAtDesc(principal.getOrganizationId()));

        CsvWriter csv = new CsvWriter().row(TICKET_HEADERS);
        for (Ticket t : tickets) {
            csv.row(t.getSubject(), t.getDescription(), t.getPriority(), t.getAccountId(), t.getContactId());
        }
        return csv.toBytes();
    }

    // ---- Import ----

    public ImportJob importAccounts(UserPrincipal principal, MultipartFile file) {
        return runImport(principal, ImportJob.EntityType.ACCOUNT, ACCOUNT_HEADERS, ACCOUNT_REQUIRED, file, this::createAccountFromRow);
    }

    public ImportJob importContacts(UserPrincipal principal, MultipartFile file) {
        return runImport(principal, ImportJob.EntityType.CONTACT, CONTACT_HEADERS, CONTACT_REQUIRED, file, this::createContactFromRow);
    }

    public ImportJob importLeads(UserPrincipal principal, MultipartFile file) {
        return runImport(principal, ImportJob.EntityType.LEAD, LEAD_HEADERS, LEAD_REQUIRED, file, this::createLeadFromRow);
    }

    public ImportJob importTickets(UserPrincipal principal, MultipartFile file) {
        return runImport(principal, ImportJob.EntityType.TICKET, TICKET_HEADERS, TICKET_REQUIRED, file, this::createTicketFromRow);
    }

    @Transactional(readOnly = true)
    public Page<ImportJob> listJobs(UserPrincipal principal, Pageable pageable) {
        return importJobRepository.findByOrganizationIdOrderByCreatedAtDesc(principal.getOrganizationId(), pageable);
    }

    @Transactional(readOnly = true)
    public ImportJob getJob(UserPrincipal principal, UUID jobId) {
        return importJobRepository.findByIdAndOrganizationId(jobId, principal.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("ImportJob", jobId));
    }

    @Transactional(readOnly = true)
    public List<ImportRowError> getJobErrors(UUID jobId) {
        return importRowErrorRepository.findByImportJobIdOrderByRowNumberAsc(jobId);
    }

    private void createAccountFromRow(UserPrincipal principal, Map<String, String> cells) {
        String name = require(cells, "Name");
        CreateAccountRequest request = new CreateAccountRequest(
                name, cells.get("Industry"), cells.get("Website"), cells.get("Phone"), cells.get("Billing Street"),
                cells.get("Billing City"), cells.get("Billing State"), cells.get("Billing Postal Code"), cells.get("Billing Country"),
                parseDecimal(cells.get("Annual Revenue"), "Annual Revenue"), parseInteger(cells.get("Employee Count"), "Employee Count"),
                cells.get("Description"), null);
        validate(request);
        accountService.create(principal, request);
    }

    private void createContactFromRow(UserPrincipal principal, Map<String, String> cells) {
        String firstName = require(cells, "First Name");
        String lastName = require(cells, "Last Name");
        UUID accountId = parseUuid(cells.get("Account Id"), "Account Id");
        CreateContactRequest request = new CreateContactRequest(
                firstName, lastName, cells.get("Email"), cells.get("Phone"), cells.get("Title"), cells.get("Description"), accountId, null);
        validate(request);
        contactService.create(principal, request);
    }

    private void createLeadFromRow(UserPrincipal principal, Map<String, String> cells) {
        String firstName = require(cells, "First Name");
        String lastName = require(cells, "Last Name");
        Lead.Source source = parseSource(require(cells, "Source"));
        CreateLeadRequest request = new CreateLeadRequest(
                firstName, lastName, cells.get("Email"), cells.get("Phone"), cells.get("Company Name"), cells.get("Title"), source,
                cells.get("Description"), null);
        validate(request);
        leadService.create(principal, request);
    }

    private void createTicketFromRow(UserPrincipal principal, Map<String, String> cells) {
        String subject = require(cells, "Subject");
        Ticket.Priority priority = parsePriority(cells.get("Priority"));
        UUID accountId = parseUuid(cells.get("Account Id"), "Account Id");
        UUID contactId = parseUuid(cells.get("Contact Id"), "Contact Id");
        CreateTicketRequest request = new CreateTicketRequest(subject, cells.get("Description"), priority, accountId, contactId, null);
        validate(request);
        ticketService.create(principal, request);
    }

    /**
     * Runs the exact same Bean Validation constraints ({@code @NotBlank}, {@code @Size}, {@code
     * @Email}, ...) already declared on each {@code Create*Request} record - normally applied by
     * Spring MVC's {@code @Valid @RequestBody} handling, which a hand-built request from a CSV row
     * never goes through. Reusing the annotations instead of re-implementing their limits here means
     * a column length or format rule only ever has to change in one place, and it turns what would
     * otherwise be an ugly {@code DataIntegrityViolationException} from an oversized column at
     * INSERT time into a clean, row-specific message instead.
     */
    private <T> void validate(T request) {
        Set<ConstraintViolation<T>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .map(v -> "%s %s".formatted(v.getPropertyPath(), v.getMessage()))
                    .collect(Collectors.joining("; "));
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * The shared engine every {@code import*} method above drives. Parses the file, validates the
     * header once, then hands each data row to {@code rowImporter} one at a time - a failure on row
     * 7 has no effect on rows 1-6 (already committed by the time row 7 runs) or rows 8+ (processed
     * regardless). See this class's javadoc for why that requires this method to stay
     * un-{@code @Transactional}.
     */
    private ImportJob runImport(
            UserPrincipal principal, ImportJob.EntityType entityType, List<String> headers, List<String> required,
            MultipartFile file, BiConsumer<UserPrincipal, Map<String, String>> rowImporter) {
        List<List<String>> rows;
        try {
            rows = CsvParser.parse(file.getInputStream());
        } catch (IOException e) {
            return persistFailedJob(principal, entityType, "Could not read the uploaded file: " + e.getMessage());
        }
        if (rows.isEmpty()) {
            return persistFailedJob(principal, entityType, "The uploaded file is empty");
        }

        Map<String, Integer> headerIndex = indexHeader(rows.get(0));
        List<String> missing = required.stream().filter(h -> !headerIndex.containsKey(normalize(h))).toList();
        if (!missing.isEmpty()) {
            return persistFailedJob(principal, entityType, "Missing required column(s): " + String.join(", ", missing));
        }

        List<ImportRowError> errors = new ArrayList<>();
        int successCount = 0;
        for (int rowNumber = 1; rowNumber < rows.size(); rowNumber++) {
            Map<String, String> cells = toCellMap(headers, headerIndex, rows.get(rowNumber));
            try {
                rowImporter.accept(principal, cells);
                successCount++;
            } catch (Exception e) {
                errors.add(new ImportRowError(null, rowNumber, describeError(e)));
            }
        }

        ImportJob job = new ImportJob(principal.getOrganizationId(), entityType, principal.getId());
        job.setTotalRows(rows.size() - 1);
        job.setSuccessCount(successCount);
        job.setErrorCount(errors.size());
        importJobRepository.save(job);
        if (!errors.isEmpty()) {
            errors.forEach(e -> e.setImportJobId(job.getId()));
            importRowErrorRepository.saveAll(errors);
        }
        return job;
    }

    private ImportJob persistFailedJob(UserPrincipal principal, ImportJob.EntityType entityType, String reason) {
        ImportJob job = ImportJob.failed(principal.getOrganizationId(), entityType, principal.getId());
        importJobRepository.save(job);
        importRowErrorRepository.save(new ImportRowError(job.getId(), 0, reason));
        return job;
    }

    private Map<String, Integer> indexHeader(List<String> headerRow) {
        Map<String, Integer> index = new LinkedHashMap<>();
        for (int i = 0; i < headerRow.size(); i++) {
            index.putIfAbsent(normalize(headerRow.get(i)), i);
        }
        return index;
    }

    private Map<String, String> toCellMap(List<String> headers, Map<String, Integer> headerIndex, List<String> row) {
        Map<String, String> cells = new LinkedHashMap<>();
        for (String header : headers) {
            Integer i = headerIndex.get(normalize(header));
            String raw = (i != null && i < row.size()) ? row.get(i).trim() : "";
            cells.put(header, raw.isEmpty() ? null : raw);
        }
        return cells;
    }

    private String normalize(String header) {
        return header.trim().toUpperCase(Locale.ROOT);
    }

    private String require(Map<String, String> cells, String column) {
        String value = cells.get(column);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(column + " is required");
        }
        return value;
    }

    private BigDecimal parseDecimal(String raw, String column) {
        if (raw == null) return null;
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("%s '%s' is not a valid number".formatted(column, raw));
        }
    }

    private Integer parseInteger(String raw, String column) {
        if (raw == null) return null;
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("%s '%s' is not a valid whole number".formatted(column, raw));
        }
    }

    private UUID parseUuid(String raw, String column) {
        if (raw == null) return null;
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("%s '%s' is not a valid id".formatted(column, raw));
        }
    }

    private Lead.Source parseSource(String raw) {
        try {
            return Lead.Source.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Unknown source '%s' - expected one of %s".formatted(raw, Arrays.toString(Lead.Source.values())));
        }
    }

    /** Blank defaults to MEDIUM, matching {@code Ticket.priority}'s own column default - unlike Lead's Source, a missing Priority isn't an error. */
    private Ticket.Priority parsePriority(String raw) {
        if (raw == null) return Ticket.Priority.MEDIUM;
        try {
            return Ticket.Priority.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Unknown priority '%s' - expected one of %s".formatted(raw, Arrays.toString(Ticket.Priority.values())));
        }
    }

    private String describeError(Exception e) {
        if (e instanceof BusinessException be) {
            return be.getMessage();
        }
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }
}
