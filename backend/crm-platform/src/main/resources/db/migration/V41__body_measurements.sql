-- Body measurements: a periodic, point-in-time snapshot of a client's physical stats (weight,
-- body fat %, key circumferences) recorded by a coach against one Contact - the eleventh module
-- this session, and the fifth to lean into the "ai-trainer-crm" fitness theme. Distinct from
-- ClientGoal (V36), which is a single named OBJECTIVE row with startValue/targetValue/
-- currentValue - one goal, one "current" number, no history of every reading that produced it.
-- BodyMeasurement is the opposite shape: a real time series, many rows per contact (one per
-- check-in), append-only. TrainingSession/TrainingSessionExercise (V37/V39) log workout
-- activity, not body metrics. NutritionPlan (V40) is a forward-looking prescription, not an
-- outcome measurement. No FK to client_goals or any other fitness table - same "no FK between
-- independently-useful peer resources" restraint training_sessions'/nutrition_plans' own
-- migration comments already established; correlating a measurement trend with a goal is a
-- reporting-time join on contact_id, not a hard dependency. Progress photos are deliberately NOT
-- a column here - attachment.related_to_type already includes CONTACT, so photo check-ins ride
-- on the existing attachment module rather than duplicating file-storage plumbing.
--
-- Owner-scoped CRM-resource pattern, mirrors nutrition_plans (V40) and client_goals (V36)
-- exactly: full OWN/TEAM/DEPARTMENT/ORGANIZATION ladder, joins RoleService#isCoreCrmResource,
-- the same restrained CREATE/READ/UPDATE/DELETE action set (no EXPORT/IMPORT/ASSIGN). owner_id
-- is the coach who recorded the check-in; contact_id (a real, non-nullable FK) is the client
-- measured - never the authorization subject, same "owner and target are different people"
-- split client_goals/training_sessions/nutrition_plans already established.
--
-- No status column, unlike tickets/contracts/client_goals/training_sessions/nutrition_plans - a
-- body-measurement check-in is an append-only, point-in-time log entry (like
-- training_session_exercises, or like attachments - a file doesn't move through states either),
-- not a resource with a lifecycle. There's nothing to transition: the record either exists or is
-- soft-deleted, so inventing a meaningless status field purely for consistency would be wrong.
--
-- measured_at is a separate date field from created_at (BaseEntity) - a coach may backfill an
-- entry for a check-in that happened a few days ago, same reasoning training_sessions.started_at
-- is distinct from created_at.

insert into permissions (resource, action, scope, description)
select resource, action, scope, initcap(replace(resource, '_', ' ')) || ': ' || initcap(action) || ' (' || initcap(scope) || ' scope)'
from (values ('BODY_MEASUREMENT')) as r(resource)
cross join (values ('CREATE'), ('READ'), ('UPDATE'), ('DELETE')) as a(action)
cross join (values ('OWN'), ('TEAM'), ('DEPARTMENT'), ('ORGANIZATION')) as s(scope);

create table body_measurements (
    id                  uuid primary key default gen_random_uuid(),
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now(),
    created_by          uuid,
    updated_by          uuid,
    version             bigint not null default 0,
    organization_id     uuid not null references organizations (id),
    contact_id          uuid not null references contacts (id),
    owner_id            uuid not null references users (id),
    measured_at         date not null,
    weight_value        numeric(6,2),
    weight_unit         varchar(10),
    body_fat_percent    numeric(5,2),
    chest_cm            numeric(6,2),
    waist_cm            numeric(6,2),
    hips_cm             numeric(6,2),
    notes               varchar(2000),
    deleted_at          timestamptz
);

create index idx_body_measurements_organization_id on body_measurements (organization_id);
create index idx_body_measurements_owner_id on body_measurements (organization_id, owner_id);
create index idx_body_measurements_contact_id on body_measurements (contact_id);
create index idx_body_measurements_measured_at on body_measurements (organization_id, contact_id, measured_at);
