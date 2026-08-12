-- Daily, per-(organization, owner, stage) snapshots of the sales pipeline - trend-over-time on
-- top of what report/'s PipelineStageSummaryDto and OwnerStageAggregateDto already give (both are
-- computed live, on every request, straight off the current opportunities table - there is
-- nowhere in this platform that remembers what the pipeline looked like yesterday, last week, or
-- last quarter until this table exists). A genuinely different concept from report/, not a
-- duplicate of it: report/ is a view with no entity of its own, recomputed from scratch on every
-- call; forecast/ is a materialized, persisted history that a live query structurally cannot
-- reconstruct after the fact, because a closed or reassigned Opportunity no longer shows up the
-- way it did the day it was captured.
--
-- No new permission is seeded here. forecast/'s only writer is a @Scheduled job
-- (PipelineSnapshotService#captureDaily) - there is no create/update/delete endpoint for a client
-- to call - and its only reader reuses REPORT:READ via ScopeAuthorizationService#visibleOwnerIds,
-- exactly the same "lean on an existing permission rather than invent a redundant one" reasoning
-- dashboard/'s and sla/'s own read paths already established (see backend/crm-platform/
-- README.md's module layout for both). Seeing a snapshot of pipeline someone could already see
-- live shouldn't require a second, parallel permission.
create table pipeline_snapshots (
    id             uuid primary key default gen_random_uuid(),
    created_at     timestamptz not null default now(),
    updated_at     timestamptz not null default now(),
    created_by     uuid,
    updated_by     uuid,
    version        bigint not null default 0,
    organization_id uuid not null references organizations (id),
    snapshot_date  date not null,
    owner_id       uuid not null references users (id),
    stage          varchar(30) not null,
    deal_count     int not null,
    total_value    numeric(15, 2) not null,

    -- One row per (org, date, owner, stage) - captureDaily deletes and re-inserts a day's rows
    -- rather than upserting, so a job re-run for the same day (or a delayed run that captures
    -- yesterday) is naturally idempotent without needing find-or-create per row.
    constraint uq_pipeline_snapshots_org_date_owner_stage unique (organization_id, snapshot_date, owner_id, stage)
);

-- The exact lookup a trend page runs: one organization, a date range, ordered chronologically.
create index idx_pipeline_snapshots_org_date on pipeline_snapshots (organization_id, snapshot_date);
