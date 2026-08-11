-- Activities: the shared log of everything a rep does against a CRM record -
-- calls, emails, meetings, tasks, and freeform notes. One table for all five
-- types (see role.entity.Permission.Resource.ACTIVITY, already seeded in V2)
-- rather than a table per type: they share every column (who, what, when,
-- related-to-what), and CrmAuditEvents already established the "one shape,
-- tagged with a type" pattern for exactly this reason.
--
-- related_to_id is deliberately a plain uuid with NO foreign key, unlike
-- every other cross-entity reference in V3 - an activity can point at an
-- account, a contact, an opportunity, or a lead, and Postgres has no notion
-- of "an FK that targets one of these four tables depending on a sibling
-- column." ActivityService validates the reference (existence + tenant)
-- explicitly at write time instead, the same way it already has to for
-- owner_id pointing at users.

create table activities (
    id                uuid primary key default gen_random_uuid(),
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now(),
    created_by        uuid,
    updated_by        uuid,
    version           bigint not null default 0,
    organization_id   uuid not null references organizations (id),
    type              varchar(20) not null,
    subject           varchar(200) not null,
    description       varchar(2000),
    status            varchar(20) not null default 'OPEN',
    priority           varchar(10),
    due_at            timestamptz,
    completed_at      timestamptz,
    related_to_type   varchar(20) not null,
    related_to_id     uuid not null,
    owner_id          uuid not null references users (id)
);

create index idx_activities_organization_id on activities (organization_id);
create index idx_activities_owner_id on activities (organization_id, owner_id);
create index idx_activities_related_to on activities (organization_id, related_to_type, related_to_id);
create index idx_activities_owner_status on activities (organization_id, owner_id, status);
