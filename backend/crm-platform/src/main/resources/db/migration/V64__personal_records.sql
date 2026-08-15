-- Personal Record: a client's best-ever result for one Exercise, in one of four record types.
-- Co-located in the exercise package (not a new personalrecord package) so
-- PersonalRecordService can call ExerciseService's package-private findOrThrow when validating a
-- record's parent exercise - same precedent RoomBookingService established for Room.
--
-- Owner-scoped, full OWN/TEAM/DEPARTMENT/ORGANIZATION ladder, same shape every other occurrence
-- entity in this platform uses. All four record_type values share "higher is better" semantics
-- (one_rep_max, max_reps, max_weight, max_duration_seconds all reward a bigger number) - a
-- deliberate simplification that keeps PersonalRecordService#assertIsImprovement's single
-- "value > currentBest" comparison universally correct without per-type comparison direction
-- logic. A new record for a given contact+exercise+record_type must beat the existing best or the
-- create is rejected outright - this is the first "reject if not better than existing record"
-- business rule in the platform.

insert into permissions (resource, action, scope, description)
select resource, action, scope, initcap(replace(resource, '_', ' ')) || ': ' || initcap(action) || ' (' || initcap(scope) || ' scope)'
from (values ('PERSONAL_RECORD')) as r(resource)
cross join (values ('CREATE'), ('READ'), ('UPDATE'), ('DELETE')) as a(action)
cross join (values ('OWN'), ('TEAM'), ('DEPARTMENT'), ('ORGANIZATION')) as s(scope);

create table personal_records (
    id                  uuid primary key default gen_random_uuid(),
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now(),
    created_by          uuid,
    updated_by          uuid,
    version             bigint not null default 0,
    organization_id     uuid not null references organizations (id),
    contact_id          uuid not null references contacts (id),
    exercise_id         uuid not null references exercises (id),
    owner_id            uuid not null references users (id),
    record_type         varchar(30) not null,
    value                numeric(10, 2) not null,
    achieved_at         timestamptz not null default now(),
    notes               varchar(2000),
    deleted_at          timestamptz
);

create index idx_personal_records_organization_id on personal_records (organization_id);
create index idx_personal_records_owner_id on personal_records (organization_id, owner_id);
create index idx_personal_records_contact_exercise_type on personal_records (contact_id, exercise_id, record_type);
