-- Sales engagement sequences (a.k.a. cadences): a reusable, ordered set of outreach touches
-- (EMAIL/CALL/TASK, each at a day offset from enrollment) that a Lead or Contact gets worked
-- through - the second module this session, alongside course/certification (V31), that's real new
-- functional surface area rather than a variation on an existing one.
--
-- Three tables, two independent shapes, both already established elsewhere in this codebase:
--
--   sequences         admin/sales-ops-maintained catalog, same no-ownerId shape as courses (V31)
--                     and products (V5) - SEQUENCE gets TEAM/DEPARTMENT/ORGANIZATION scope only
--                     (no OWN), no ScopeAuthorizationService call in SequenceService.
--
--   sequence_steps    a real child of exactly one sequence (like quote_line_items -> quotes, V5) -
--                     a real FK with cascade delete, no permission of its own, managed entirely
--                     through SequenceService the same way QuoteService owns its line items.
--
--   sequence_enrollments   a normal owner-scoped CRM record, same shape course_enrollments (V31)
--                     uses: SEQUENCE_ENROLLMENT gets the full OWN/TEAM/DEPARTMENT/ORGANIZATION
--                     ladder and joins RoleService#isCoreCrmResource, so a default MEMBER holds
--                     OWN+TEAM CREATE/READ/UPDATE. owner_id here plays its usual role (the rep
--                     working the sequence), separate from target_id (the Lead or Contact being
--                     worked) - unlike CourseEnrollment/UserCertification, where the "owner" and
--                     the thing being tracked were the same person, this module has both roles
--                     because a sequence is worked BY a rep ON a prospect, not something a rep
--                     enrolls themselves into.
--
-- No automated sending lives here, deliberately - the same restraint EmailMessage (V15) already
-- takes with "log what was sent, don't be the SMTP client." SequenceEnrollmentService only tracks
-- position (current_step_index) and status; a rep marks the current step done and the enrollment
-- advances, or completes/pauses/cancels it. Building a real scheduler that fires emails
-- automatically at each day_offset is a materially different (and much riskier) feature this pass
-- doesn't take on.

insert into permissions (resource, action, scope, description)
select resource, action, scope, initcap(replace(resource, '_', ' ')) || ': ' || initcap(action) || ' (' || initcap(scope) || ' scope)'
from (values ('SEQUENCE')) as r(resource)
cross join (values ('CREATE'), ('READ'), ('UPDATE'), ('DELETE')) as a(action)
cross join (values ('TEAM'), ('DEPARTMENT'), ('ORGANIZATION')) as s(scope);

insert into permissions (resource, action, scope, description)
select resource, action, scope, initcap(replace(resource, '_', ' ')) || ': ' || initcap(action) || ' (' || initcap(scope) || ' scope)'
from (values ('SEQUENCE_ENROLLMENT')) as r(resource)
cross join (values ('CREATE'), ('READ'), ('UPDATE'), ('DELETE')) as a(action)
cross join (values ('OWN'), ('TEAM'), ('DEPARTMENT'), ('ORGANIZATION')) as s(scope);

create table sequences (
    id                uuid primary key default gen_random_uuid(),
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now(),
    created_by        uuid,
    updated_by        uuid,
    version           bigint not null default 0,
    organization_id   uuid not null references organizations (id),
    name              varchar(200) not null,
    description       varchar(2000),
    active            boolean not null default true,
    deleted_at        timestamptz
);

create index idx_sequences_organization_id on sequences (organization_id);

create table sequence_steps (
    id             uuid primary key default gen_random_uuid(),
    created_at     timestamptz not null default now(),
    updated_at     timestamptz not null default now(),
    created_by     uuid,
    updated_by     uuid,
    version        bigint not null default 0,
    sequence_id    uuid not null references sequences (id) on delete cascade,
    step_order     integer not null,
    type           varchar(20) not null,
    day_offset     integer not null default 0,
    subject        varchar(200),
    body           varchar(4000)
);

create index idx_sequence_steps_sequence_id on sequence_steps (sequence_id, step_order);

create table sequence_enrollments (
    id                   uuid primary key default gen_random_uuid(),
    created_at           timestamptz not null default now(),
    updated_at           timestamptz not null default now(),
    created_by           uuid,
    updated_by           uuid,
    version              bigint not null default 0,
    organization_id      uuid not null references organizations (id),
    sequence_id          uuid not null references sequences (id),
    target_type          varchar(20) not null,
    target_id            uuid not null,
    owner_id             uuid not null references users (id),
    current_step_index   integer not null default 0,
    status               varchar(20) not null default 'ACTIVE',
    enrolled_at          timestamptz not null default now(),
    completed_at         timestamptz,
    deleted_at           timestamptz
);

create index idx_sequence_enrollments_organization_id on sequence_enrollments (organization_id);
create index idx_sequence_enrollments_sequence_id on sequence_enrollments (organization_id, sequence_id);
create index idx_sequence_enrollments_owner_id on sequence_enrollments (organization_id, owner_id);
-- Same "no second concurrent active enrollment" reasoning uq_course_enrollments_course_user_active
-- (V31) documents - a target already actively being worked through a sequence can't be double-
-- enrolled into the same one; re-enrolling after this row is deleted is still allowed.
create unique index uq_sequence_enrollments_target_active
    on sequence_enrollments (organization_id, sequence_id, target_type, target_id) where deleted_at is null;
