-- Class Scheduling & Attendance: group fitness classes (spin, bootcamp, yoga) as opposed to the
-- 1:1 work TrainingSession (V37) and BookingLink (V33) already cover. Nothing in the schema
-- today models "N clients show up to the same scheduled session with a capacity limit" - a real
-- gym/studio need distinct from both a single trainer-client session and a public self-booking
-- link for 1:1 time.
--
-- Three tables, a new three-level shape (catalog -> occurrence -> roster) rather than the
-- two-level Product/Quote or MembershipPlan/Membership split, because a class genuinely has three
-- different lifecycles layered on each other:
--
-- group_classes is the shared organization CATALOG of class *types* ("Spin 45", "Sunrise Yoga") -
-- same shape as PRODUCT/MEMBERSHIP_PLAN: TEAM/DEPARTMENT/ORGANIZATION only, no OWN, no owner_id.
-- default_instructor_id is a soft default copied onto new sessions, not a foreign key the
-- catalog row is authorized against - GroupClassService does no per-record ScopeAuthorizationService
-- check, identical reasoning to ProductService/MembershipPlanService.
--
-- class_sessions is one scheduled OCCURRENCE of a group_class - owner-scoped like
-- Membership/ClientGoal, full OWN/TEAM/DEPARTMENT/ORGANIZATION ladder, so CLASS_SESSION joins
-- RoleService#isCoreCrmResource. Its owner_id is the instructor actually running that occurrence
-- (defaults to the caller, same resolveOwner pattern as every other owner-scoped module - an
-- admin can schedule a session under a different instructor, a plain instructor can only
-- schedule their own). status is a free state machine (SCHEDULED/CANCELLED/COMPLETED) - a
-- cancelled session being reinstated is a normal correction, not blocked.
--
-- class_attendances is the roster: one contact's registration for one class_session. Also
-- owner-scoped and also joins isCoreCrmResource, but its owner_id is *copied* from the parent
-- session's owner_id at creation time rather than resolved independently - the person who can
-- manage a session's roster is the same person (or broader scope) who can manage the session
-- itself, so there is no separate "assign this attendance to someone else" concept the way
-- Membership/ClientGoal have. registered_at is stamped once at creation (never recomputed);
-- checked_in_at is nullable and set the first time status moves to ATTENDED, the same
-- "stamped once, never overwritten" rule contracts.signed_at/client_goals.achieved_at
-- established - a client checking in, being marked NO_SHOW by mistake, and corrected back to
-- ATTENDED should not lose their original check-in time. ClassAttendanceService enforces
-- capacity (session.capacity_override, falling back to the parent group_class's capacity, null
-- meaning unlimited) by rejecting a new REGISTERED/ATTENDED row once the session is full - the
-- one piece of real cross-record business logic this module adds beyond the by-now-familiar CRUD
-- shape.

insert into permissions (resource, action, scope, description)
select resource, action, scope, initcap(replace(resource, '_', ' ')) || ': ' || initcap(action) || ' (' || initcap(scope) || ' scope)'
from (values ('GROUP_CLASS')) as r(resource)
cross join (values ('CREATE'), ('READ'), ('UPDATE'), ('DELETE')) as a(action)
cross join (values ('TEAM'), ('DEPARTMENT'), ('ORGANIZATION')) as s(scope);

insert into permissions (resource, action, scope, description)
select resource, action, scope, initcap(replace(resource, '_', ' ')) || ': ' || initcap(action) || ' (' || initcap(scope) || ' scope)'
from (values ('CLASS_SESSION'), ('CLASS_ATTENDANCE')) as r(resource)
cross join (values ('CREATE'), ('READ'), ('UPDATE'), ('DELETE')) as a(action)
cross join (values ('OWN'), ('TEAM'), ('DEPARTMENT'), ('ORGANIZATION')) as s(scope);

create table group_classes (
    id                     uuid primary key default gen_random_uuid(),
    created_at             timestamptz not null default now(),
    updated_at             timestamptz not null default now(),
    created_by             uuid,
    updated_by             uuid,
    version                bigint not null default 0,
    organization_id        uuid not null references organizations (id),
    name                   varchar(200) not null,
    description            varchar(2000),
    default_instructor_id  uuid references users (id),
    duration_minutes       integer not null default 60,
    capacity               integer,
    location               varchar(200),
    active                 boolean not null default true,
    deleted_at             timestamptz
);

create index idx_group_classes_organization_id on group_classes (organization_id);

create table class_sessions (
    id                  uuid primary key default gen_random_uuid(),
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now(),
    created_by          uuid,
    updated_by          uuid,
    version             bigint not null default 0,
    organization_id     uuid not null references organizations (id),
    group_class_id      uuid not null references group_classes (id),
    owner_id            uuid not null references users (id),
    starts_at           timestamptz not null,
    ends_at             timestamptz not null,
    capacity_override   integer,
    status              varchar(20) not null default 'SCHEDULED',
    notes               varchar(2000),
    deleted_at          timestamptz
);

create index idx_class_sessions_organization_id on class_sessions (organization_id);
create index idx_class_sessions_owner_id on class_sessions (organization_id, owner_id);
create index idx_class_sessions_group_class_id on class_sessions (group_class_id);
create index idx_class_sessions_starts_at on class_sessions (organization_id, starts_at);

create table class_attendances (
    id                 uuid primary key default gen_random_uuid(),
    created_at         timestamptz not null default now(),
    updated_at         timestamptz not null default now(),
    created_by         uuid,
    updated_by         uuid,
    version            bigint not null default 0,
    organization_id    uuid not null references organizations (id),
    class_session_id   uuid not null references class_sessions (id),
    contact_id         uuid not null references contacts (id),
    owner_id           uuid not null references users (id),
    status             varchar(20) not null default 'REGISTERED',
    registered_at      timestamptz not null default now(),
    checked_in_at      timestamptz,
    notes              varchar(500),
    deleted_at         timestamptz
);

create index idx_class_attendances_organization_id on class_attendances (organization_id);
create index idx_class_attendances_owner_id on class_attendances (organization_id, owner_id);
create index idx_class_attendances_session_id on class_attendances (class_session_id);
create index idx_class_attendances_contact_id on class_attendances (contact_id);
