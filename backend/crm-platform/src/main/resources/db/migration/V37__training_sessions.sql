-- Training sessions: the post-session record of what actually happened in a coaching session -
-- the seventh module this session, and the second (after client_goals, V36) to lean into what
-- this platform's own name ("ai-trainer-crm") implies. Deliberately distinct from booking_links/
-- booking_slots (V33), which are the PRE-session scheduling mechanism ("book time with me");
-- once a slot is booked and the session happens, this module is where the coach logs what was
-- actually done - duration, focus area, the client's own effort rating, and freeform notes. A
-- booking_slot_id is optionally carried over (nullable - plenty of sessions get logged without
-- ever having gone through the booking flow, e.g. a walk-in or a session set up over text) purely
-- as a cross-reference, not a requirement.
--
-- Also distinct from client_goals (V36): a ClientGoal is the long-term target ("lose 15 lbs by
-- March"); a TrainingSession is one unit of work performed toward it ("Tuesday's session,
-- 45 minutes, focus on lower body"). Neither table references the other - correlating them is a
-- reporting-time join on contact_id, not a hard FK, the same "no FK between two independently-
-- useful owner-scoped resources unless one is actually a child of the other" restraint quote_line_
-- items/booking_slots (true parent-child) don't extend to peer resources like this.
--
-- Owner-scoped CRM-resource pattern, mirrors client_goals/contracts exactly: full OWN/TEAM/
-- DEPARTMENT/ORGANIZATION ladder, joins RoleService#isCoreCrmResource, the same restrained
-- CREATE/READ/UPDATE/DELETE action set (no EXPORT/IMPORT/ASSIGN). contact_id (the client) is a
-- required real FK, never the authorization subject - same split every owner-target module this
-- session already established. status is a free (non-linear) state machine like tickets/
-- contracts/client_goals - a NO_SHOW can be corrected back to SCHEDULED if it was logged in
-- error, no invalid-transition rule needed.

insert into permissions (resource, action, scope, description)
select resource, action, scope, initcap(replace(resource, '_', ' ')) || ': ' || initcap(action) || ' (' || initcap(scope) || ' scope)'
from (values ('TRAINING_SESSION')) as r(resource)
cross join (values ('CREATE'), ('READ'), ('UPDATE'), ('DELETE')) as a(action)
cross join (values ('OWN'), ('TEAM'), ('DEPARTMENT'), ('ORGANIZATION')) as s(scope);

create table training_sessions (
    id                 uuid primary key default gen_random_uuid(),
    created_at         timestamptz not null default now(),
    updated_at         timestamptz not null default now(),
    created_by         uuid,
    updated_by         uuid,
    version            bigint not null default 0,
    organization_id    uuid not null references organizations (id),
    contact_id         uuid not null references contacts (id),
    owner_id           uuid not null references users (id),
    booking_slot_id    uuid references booking_slots (id),
    started_at         timestamptz not null,
    duration_minutes   integer not null default 60,
    session_type       varchar(20) not null default 'IN_PERSON',
    status              varchar(20) not null default 'SCHEDULED',
    focus_area         varchar(200),
    -- Client's own perceived-effort rating for the session (Rate of Perceived Exertion, a
    -- standard 1-10 fitness-coaching scale) - nullable, since it's typically only recorded after
    -- COMPLETED, not at SCHEDULED/CANCELLED time.
    client_rpe         integer,
    coach_notes        varchar(2000),
    deleted_at         timestamptz
);

create index idx_training_sessions_organization_id on training_sessions (organization_id);
create index idx_training_sessions_owner_id on training_sessions (organization_id, owner_id);
create index idx_training_sessions_contact_id on training_sessions (contact_id);
create index idx_training_sessions_status on training_sessions (organization_id, status);
