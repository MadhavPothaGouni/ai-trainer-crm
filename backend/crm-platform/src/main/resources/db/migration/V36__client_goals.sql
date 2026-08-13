-- Client goals: a coach/trainer-defined, freeform measurable objective (weight, strength,
-- endurance, or a custom metric) tracked against one Contact over time - the sixth module this
-- session, and the first to lean into what this platform's own name ("ai-trainer-crm") actually
-- implies rather than staying purely generic-CRM. Distinct from everything already in the
-- schema: CourseEnrollment (V31) tracks progress THROUGH a specific training course's content;
-- SalesGoal (V25) tracks an internal rep's own quota, never a customer's; Contract (V35) tracks
-- legal/subscription terms. Nothing else tracks "this client is trying to go from X to Y on
-- some metric, by some date."
--
-- Owner-scoped CRM-resource pattern, mirrors contracts (V35) and tickets (V14) exactly: full
-- OWN/TEAM/DEPARTMENT/ORGANIZATION ladder, joins RoleService#isCoreCrmResource, the same
-- restrained CREATE/READ/UPDATE/DELETE action set (no EXPORT/IMPORT/ASSIGN) contracts/
-- booking_links/course_enrollments already use. owner_id is the coach/trainer responsible;
-- contact_id (a real FK, not nullable) is the client the goal is FOR - never the authorization
-- subject, same "owner and target are different people" split sequence_enrollments (V32)
-- established for a rep working a prospect through a sequence.
--
-- status is a free (non-linear) state machine, same restraint tickets.status/contracts.status
-- already take - putting an abandoned goal back to ACTIVE is a legitimate correction, not an
-- invalid transition. achieved_at is stamped the first time status moves to ACHIEVED and never
-- overwritten afterward, the same "snapshot, don't let it drift" rule contracts.signed_at
-- (V35) and user_certifications.expires_at (V31) already document.
--
-- start_value/target_value/current_value are numeric(10, 2), not numeric(14, 2) like money
-- columns elsewhere in this schema (products.unit_price, contracts.total_value) - these are
-- physical/performance metrics (pounds, reps, minutes, ...), not currency, so the smaller
-- precision is intentional, and metric_unit is a free-text label ("lbs", "reps", "5k time")
-- rather than an enum, since a CUSTOM goal_type can measure literally anything.

insert into permissions (resource, action, scope, description)
select resource, action, scope, initcap(replace(resource, '_', ' ')) || ': ' || initcap(action) || ' (' || initcap(scope) || ' scope)'
from (values ('CLIENT_GOAL')) as r(resource)
cross join (values ('CREATE'), ('READ'), ('UPDATE'), ('DELETE')) as a(action)
cross join (values ('OWN'), ('TEAM'), ('DEPARTMENT'), ('ORGANIZATION')) as s(scope);

create table client_goals (
    id                uuid primary key default gen_random_uuid(),
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now(),
    created_by        uuid,
    updated_by        uuid,
    version           bigint not null default 0,
    organization_id   uuid not null references organizations (id),
    contact_id        uuid not null references contacts (id),
    owner_id          uuid not null references users (id),
    title             varchar(200) not null,
    goal_type         varchar(20) not null default 'CUSTOM',
    metric_unit       varchar(30),
    start_value       numeric(10, 2),
    target_value      numeric(10, 2),
    current_value     numeric(10, 2),
    target_date       date,
    status            varchar(20) not null default 'ACTIVE',
    achieved_at       timestamptz,
    notes             varchar(2000),
    deleted_at        timestamptz
);

create index idx_client_goals_organization_id on client_goals (organization_id);
create index idx_client_goals_owner_id on client_goals (organization_id, owner_id);
create index idx_client_goals_contact_id on client_goals (contact_id);
create index idx_client_goals_status on client_goals (organization_id, status);
