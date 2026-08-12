-- A named, personal filter+sort combination for one of the standard list
-- pages (Lead/Contact/Account/Opportunity/Ticket) - "My hot leads",
-- "Enterprise accounts I own", etc. No permission catalog entry at all:
-- this is the purest instance yet of the fourth-kind, notification-style
-- self-scoped shape (see notification/'s V17 migration comment) - purer
-- than notification/ itself, because every single action here (list,
-- create, update, delete, set-default) is scoped to the caller's own id,
-- with no sender/recipient split to complicate it. A saved view is never
-- shared, never visible to a manager, never something an ADMIN role
-- reaches into - it's pure personal UI configuration, the same category
-- of thing as a browser bookmark, so there's no scope level (OWN/TEAM/
-- ORGANIZATION) that could ever mean anything but "yourself."
--
-- filters is a free-form JSON blob (stored as text, never parsed or
-- validated server-side beyond non-null) - the exact shape depends on
-- which entity_type it's for and which filter fields that list page
-- exposes, and SavedViewService has no reason to know either; the
-- frontend owns encoding/decoding it, the same "backend stores an opaque
-- value, frontend owns its shape" reasoning attachment.storageKey uses
-- for a different kind of opacity.
--
-- is_default marks the one view an entity type's list page applies
-- automatically on load; the partial unique index below enforces "at most
-- one default per (organization, owner, entity_type)" at the database
-- level, the exact same defense-in-depth shape dashboards' V12
-- uq_dashboards_one_default_per_owner index already established - see
-- SavedViewService#setDefault's javadoc for why the unset-then-set write
-- order is load-bearing, not just tidy.
create table saved_views (
    id               uuid primary key default gen_random_uuid(),
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now(),
    created_by       uuid,
    updated_by       uuid,
    version          bigint not null default 0,
    organization_id  uuid not null references organizations (id),
    owner_user_id    uuid not null references users (id),
    entity_type      varchar(20) not null,
    name             varchar(150) not null,
    filters          text not null,
    sort_field       varchar(50),
    sort_direction   varchar(10),
    is_default       boolean not null default false
);

-- The exact lookup SavedViewController#list runs on every list-page load: this owner's views
-- for one entity type, cheapest-first.
create index idx_saved_views_owner on saved_views (organization_id, owner_user_id, entity_type);
create unique index uq_saved_views_one_default_per_owner_and_type
    on saved_views (organization_id, owner_user_id, entity_type) where is_default = true;
