-- Bulk CSV import/export for Account, Contact, and Lead. The {RESOURCE}:IMPORT
-- and {RESOURCE}:EXPORT permissions (LEAD/CONTACT/ACCOUNT/OPPORTUNITY/ACTIVITY/
-- QUOTE/TICKET, all four scopes) were seeded in V2 alongside every core CRM
-- resource's CREATE/READ/UPDATE/DELETE/ASSIGN, but nothing in the codebase
-- ever implemented IMPORT, and EXPORT only existed for Campaign/Knowledge
-- Article so far (see CampaignService's javadoc - "the first real
-- implementation of the EXPORT permission anywhere in this codebase"). This
-- migration and the importexport module built on it close that gap for the
-- three entities a CRM user most commonly bulk-loads from a spreadsheet:
-- Account, Contact, Lead. Opportunity/Activity/Quote/Ticket keep their
-- IMPORT/EXPORT permissions seeded-but-unimplemented for now, same as every
-- other resource waited its turn earlier this project.
--
-- import_jobs is a run log, one row per CSV upload - not a soft-deletable
-- business record, so like workflow_runs (V11) it skips deleted_at entirely.
-- status is COMPLETED for every job that got far enough to process rows
-- (even if every single row failed - see ImportExportService's javadoc for
-- why a "0 succeeded" job is still COMPLETED, not FAILED) and FAILED only
-- for a job that couldn't even start (empty file, unreadable encoding,
-- header missing a required column).
create table import_jobs (
    id                       uuid primary key default gen_random_uuid(),
    created_at               timestamptz not null default now(),
    updated_at               timestamptz not null default now(),
    created_by               uuid,
    updated_by               uuid,
    version                  bigint not null default 0,
    organization_id          uuid not null references organizations (id),
    entity_type              varchar(20) not null,
    initiated_by_user_id     uuid not null references users (id),
    status                   varchar(20) not null,
    total_rows               integer not null default 0,
    success_count            integer not null default 0,
    error_count              integer not null default 0
);

create index idx_import_jobs_org_created on import_jobs (organization_id, created_at desc);

-- One row per CSV row that failed to import. Deliberately not a foreign key
-- to accounts/contacts/leads - a row that failed never became one of those,
-- there's nothing for it to reference. import_job_id cascades on delete -
-- these rows have no meaning without their parent job, same reasoning
-- dashboard_widgets.dashboard_id (V12) and workflow_runs.workflow_id (V11) use.
create table import_row_errors (
    id                uuid primary key default gen_random_uuid(),
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now(),
    created_by        uuid,
    updated_by        uuid,
    version           bigint not null default 0,
    import_job_id     uuid not null references import_jobs (id) on delete cascade,
    row_number        integer not null,
    message           varchar(500) not null
);

create index idx_import_row_errors_job on import_row_errors (import_job_id, row_number);
