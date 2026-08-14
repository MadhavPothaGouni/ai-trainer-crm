-- Nutrition plans: a coach/trainer-authored dietary prescription (daily calorie target, macro
-- targets, and freeform guidance) for one Contact over a date range - the tenth module this
-- session, and the fourth (after client_goals V36, training_session/exercise V37-38,
-- training_session_exercises V39) to lean into what this platform's own name ("ai-trainer-crm")
-- implies. Distinct from everything already in the schema: ClientGoal (V36) tracks a long-term
-- measurable OUTCOME ("lose 15 lbs by March"); TrainingSession/TrainingSessionExercise (V37/V39)
-- log completed WORKOUTS; Exercise (V38) catalogs movements. None of the 39 existing modules
-- capture the dietary/macro-guidance side of coaching, which is a standard, distinct pillar of
-- personal training alongside programming and goal-tracking. No FK to client_goals or any other
-- fitness table - same "no FK between independently-useful peer resources" restraint
-- training_sessions' own migration comment already established for its relationship to
-- client_goals; correlating a plan with a goal is a reporting-time join on contact_id, not a
-- hard dependency.
--
-- Owner-scoped CRM-resource pattern, mirrors client_goals (V36) and contracts (V35) exactly:
-- full OWN/TEAM/DEPARTMENT/ORGANIZATION ladder, joins RoleService#isCoreCrmResource, the same
-- restrained CREATE/READ/UPDATE/DELETE action set (no EXPORT/IMPORT/ASSIGN). owner_id is the
-- coach who authored the plan; contact_id (a real, non-nullable FK) is the client the plan is
-- FOR - never the authorization subject, same "owner and target are different people" split
-- client_goals/training_sessions already established.
--
-- status is a free (non-linear) state machine, same restraint tickets.status/contracts.status/
-- client_goals.status/training_sessions.status already take - pulling an ARCHIVED plan back to
-- ACTIVE because a client resumes it is a legitimate correction, not an invalid transition. No
-- "snapshot, don't drift" timestamp is needed here (unlike client_goals.achieved_at/
-- contracts.signed_at) - nothing on this table is derived once from a mutable source; the
-- calorie/macro targets are simply the coach's current prescription, not a computed value with
-- a drift risk.
--
-- end_date is nullable (an ongoing plan with no defined end), same shape contracts.end_date
-- takes for auto-renewing contracts.

insert into permissions (resource, action, scope, description)
select resource, action, scope, initcap(replace(resource, '_', ' ')) || ': ' || initcap(action) || ' (' || initcap(scope) || ' scope)'
from (values ('NUTRITION_PLAN')) as r(resource)
cross join (values ('CREATE'), ('READ'), ('UPDATE'), ('DELETE')) as a(action)
cross join (values ('OWN'), ('TEAM'), ('DEPARTMENT'), ('ORGANIZATION')) as s(scope);

create table nutrition_plans (
    id                      uuid primary key default gen_random_uuid(),
    created_at              timestamptz not null default now(),
    updated_at              timestamptz not null default now(),
    created_by              uuid,
    updated_by              uuid,
    version                 bigint not null default 0,
    organization_id         uuid not null references organizations (id),
    contact_id              uuid not null references contacts (id),
    owner_id                uuid not null references users (id),
    title                   varchar(200) not null,
    daily_calorie_target    integer,
    protein_target_grams    integer,
    carb_target_grams       integer,
    fat_target_grams        integer,
    start_date              date,
    end_date                date,
    status                  varchar(20) not null default 'DRAFT',
    notes                   varchar(2000),
    deleted_at              timestamptz
);

create index idx_nutrition_plans_organization_id on nutrition_plans (organization_id);
create index idx_nutrition_plans_owner_id on nutrition_plans (organization_id, owner_id);
create index idx_nutrition_plans_contact_id on nutrition_plans (contact_id);
create index idx_nutrition_plans_status on nutrition_plans (organization_id, status);
