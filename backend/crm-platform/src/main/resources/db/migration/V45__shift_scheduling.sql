-- Staff Shift Scheduling: when staff are expected to work, and when they actually clocked in/out.
-- Nothing existing models this - ClassSession (V43) is when a *class* happens, not when a staff
-- member is on the clock; Team (V16) is org structure, not a schedule.
--
-- Two tables, the same catalog/occurrence split as GroupClass/ClassSession: shift_templates is
-- the shared organization catalog of recurring patterns ("Front Desk - Weekday Mornings"), no
-- owner_id, TEAM/DEPARTMENT/ORGANIZATION only - a schedule template belongs to the org, not one
-- person. shifts is the owner-scoped occurrence: one employee's actual shift on one date, full
-- OWN/TEAM/DEPARTMENT/ORGANIZATION ladder, so SHIFT (not SHIFT_TEMPLATE) joins
-- RoleService#isCoreCrmResource. shift_template_id is nullable - a shift can be ad-hoc, not every
-- shift needs to trace back to a recurring pattern.
--
-- status is a free state machine (SCHEDULED/IN_PROGRESS/COMPLETED/MISSED/CANCELLED) like every
-- other lifecycle field in this platform. clock_in_at/clock_out_at are stamped once, the first
-- time each is set, and never overwritten by a later correction - same "stamp once" rule
-- contracts.signed_at/client_goals.achieved_at established, since a clock-in time shouldn't
-- silently drift if someone re-clocks in by mistake.

insert into permissions (resource, action, scope, description)
select resource, action, scope, initcap(replace(resource, '_', ' ')) || ': ' || initcap(action) || ' (' || initcap(scope) || ' scope)'
from (values ('SHIFT_TEMPLATE')) as r(resource)
cross join (values ('CREATE'), ('READ'), ('UPDATE'), ('DELETE')) as a(action)
cross join (values ('TEAM'), ('DEPARTMENT'), ('ORGANIZATION')) as s(scope);

insert into permissions (resource, action, scope, description)
select resource, action, scope, initcap(replace(resource, '_', ' ')) || ': ' || initcap(action) || ' (' || initcap(scope) || ' scope)'
from (values ('SHIFT')) as r(resource)
cross join (values ('CREATE'), ('READ'), ('UPDATE'), ('DELETE')) as a(action)
cross join (values ('OWN'), ('TEAM'), ('DEPARTMENT'), ('ORGANIZATION')) as s(scope);

create table shift_templates (
    id                uuid primary key default gen_random_uuid(),
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now(),
    created_by        uuid,
    updated_by        uuid,
    version           bigint not null default 0,
    organization_id   uuid not null references organizations (id),
    name              varchar(200) not null,
    day_of_week       varchar(10) not null,
    start_time        time not null,
    end_time          time not null,
    role              varchar(100),
    active            boolean not null default true,
    deleted_at        timestamptz
);

create index idx_shift_templates_organization_id on shift_templates (organization_id);

create table shifts (
    id                  uuid primary key default gen_random_uuid(),
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now(),
    created_by          uuid,
    updated_by          uuid,
    version             bigint not null default 0,
    organization_id     uuid not null references organizations (id),
    shift_template_id   uuid references shift_templates (id),
    owner_id            uuid not null references users (id),
    shift_date          date not null,
    starts_at           timestamptz not null,
    ends_at             timestamptz not null,
    status              varchar(20) not null default 'SCHEDULED',
    clock_in_at         timestamptz,
    clock_out_at        timestamptz,
    notes               varchar(2000),
    deleted_at          timestamptz
);

create index idx_shifts_organization_id on shifts (organization_id);
create index idx_shifts_owner_id on shifts (organization_id, owner_id);
create index idx_shifts_shift_date on shifts (organization_id, shift_date);
