-- The saved-report/dashboard-builder feature ReportController's own
-- javadoc already called out as "not this pass" - DASHBOARD was seeded in
-- V2 at OWN/TEAM/ORGANIZATION scope (same group as WORKFLOW/REPORT, no
-- DEPARTMENT) with CRUD + MANAGE. A Dashboard is a named, owner-scoped
-- collection of DashboardWidgets; each widget doesn't store any data of
-- its own - it just names one of the three existing ReportService
-- aggregate queries (report_type) plus a bit of grid layout (display_order/
-- width/height) and an optional title override. DashboardService#getData
-- fetches each widget's live data by calling straight into ReportService
-- at read time - there's deliberately no cached/materialized result stored
-- anywhere, so a dashboard always reflects the current pipeline/funnel/
-- leaderboard, never a stale snapshot.
--
-- dashboard_widgets.dashboard_id cascades on delete (a widget has no
-- meaning without its dashboard) - same reasoning campaign_members.
-- campaign_id and custom_field_picklist_values.custom_field_id use.
--
-- is_default marks the one dashboard an owner sees first; the partial
-- unique index below enforces "at most one default per (organization,
-- owner)" at the database level rather than trusting DashboardService's
-- own bookkeeping alone - the same defense-in-depth reasoning behind every
-- other partial unique index this session (campaign_members' anti-
-- duplicate-membership indexes in V9, custom_fields' anti-duplicate-
-- api-name indexes in V10).

create table dashboards (
    id                uuid primary key default gen_random_uuid(),
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now(),
    created_by        uuid,
    updated_by        uuid,
    version           bigint not null default 0,
    organization_id   uuid not null references organizations (id),
    owner_id          uuid not null references users (id),
    name              varchar(200) not null,
    description       varchar(2000),
    is_default        boolean not null default false,
    deleted_at        timestamptz
);

create index idx_dashboards_organization_id on dashboards (organization_id);
create unique index uq_dashboards_one_default_per_owner
    on dashboards (organization_id, owner_id) where is_default = true and deleted_at is null;

create table dashboard_widgets (
    id               uuid primary key default gen_random_uuid(),
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now(),
    created_by       uuid,
    updated_by       uuid,
    version          bigint not null default 0,
    dashboard_id     uuid not null references dashboards (id) on delete cascade,
    report_type      varchar(30) not null,
    title            varchar(200),
    display_order    integer not null default 0,
    width            integer not null default 6,
    height           integer not null default 4
);

create index idx_dashboard_widgets_dashboard_id on dashboard_widgets (dashboard_id, display_order);
