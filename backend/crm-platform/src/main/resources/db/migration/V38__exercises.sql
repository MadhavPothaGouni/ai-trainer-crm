-- Exercise library: the atomic movement-library building block (a single named move like
-- "Barbell Back Squat") a coach references when planning a session - the eighth module this
-- session, and the third (after client_goals V36, training_sessions V37) to lean into what this
-- platform's own name ("ai-trainer-crm") implies. Deliberately distinct from everything else in
-- the schema: course/certification (V31) is structured, enrollable curriculum content with
-- progress/pass-fail; training_sessions (V37) is the post-session log of what happened; nothing
-- else catalogs individual exercises. training_sessions deliberately does NOT get an exercise_id
-- FK in this pass - a session's focus_area free-text column already covers "what was this
-- session about" at the level of detail this module needs, and a real
-- session-to-exercise-list join table (sets/reps/weight per exercise per session) is a
-- materially bigger, separate feature this pass doesn't take on, the same restraint macro's
-- migration comment already models for scope discipline.
--
-- Catalog-resource pattern, mirrors course/product exactly: no ownerId, EXERCISE seeded at
-- TEAM/DEPARTMENT/ORGANIZATION only (no OWN), ExerciseService makes no
-- ScopeAuthorizationService call - the controller's coarse @PreAuthorize (any of those three
-- scopes) is the whole authorization story, same as CourseService/MacroService's identical
-- reasoning.

insert into permissions (resource, action, scope, description)
select resource, action, scope, initcap(replace(resource, '_', ' ')) || ': ' || initcap(action) || ' (' || initcap(scope) || ' scope)'
from (values ('EXERCISE')) as r(resource)
cross join (values ('CREATE'), ('READ'), ('UPDATE'), ('DELETE')) as a(action)
cross join (values ('TEAM'), ('DEPARTMENT'), ('ORGANIZATION')) as s(scope);

create table exercises (
    id                      uuid primary key default gen_random_uuid(),
    created_at              timestamptz not null default now(),
    updated_at              timestamptz not null default now(),
    created_by              uuid,
    updated_by              uuid,
    version                 bigint not null default 0,
    organization_id         uuid not null references organizations (id),
    name                    varchar(200) not null,
    description             varchar(2000),
    category                varchar(20) not null,
    primary_muscle_group    varchar(20) not null,
    equipment               varchar(30) not null default 'NONE',
    difficulty_level        varchar(20) not null default 'BEGINNER',
    video_url               varchar(500),
    active                  boolean not null default true,
    deleted_at              timestamptz
);

create index idx_exercises_organization_id on exercises (organization_id);
-- Same "unique within an organization, not globally" shape uq_contracts_org_number (V35) uses -
-- two orgs can each have their own "Barbell Back Squat" entry, but one org shouldn't have two.
create unique index uq_exercises_org_name on exercises (organization_id, lower(name)) where deleted_at is null;
