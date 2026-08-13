-- Course/Training/Certification management - the module this segment adds specifically because it
-- fits "ai-trainer-crm" itself (every other module since V3 has been generic CRM surface area that
-- could belong to any sales platform; this one is about training the sales org, not selling to
-- customers). Two independent (catalog, enrollment-or-award) pairs, deliberately mirroring two
-- different existing shapes rather than inventing new ones:
--
--   courses / course_enrollments   mirrors products / quotes (V5): Course is shared catalog data an
--   admin maintains (COURSE gets TEAM/DEPARTMENT/ORGANIZATION scope, no OWN - same reasoning V5's
--   comment gives for products), CourseEnrollment is a normal owner-scoped CRM record (COURSE_ENROLLMENT
--   gets the full OWN/TEAM/DEPARTMENT/ORGANIZATION ladder and joins RoleService#isCoreCrmResource, the
--   same treatment quotes/tickets/activities get) whose owner is the *enrolled user*, not a salesperson
--   working a deal.
--
--   certifications / user_certifications   the identical shape one level up: Certification is the
--   admin-maintained catalog of credentials the org recognizes (issuing body, how long one lasts),
--   UserCertification is the owner-scoped record of one person actually holding one, renewed or
--   revoked over time - a user can hold more than one UserCertification row for the same
--   Certification (recertification), unlike CourseEnrollment which is unique per (course, user)
--   while active.
--
-- Neither pair reads or writes anything in lead/opportunity/etc - this module has no effect on the
-- sales pipeline itself, only on who's trained/credentialed to work it, which is exactly why it's
-- new functional surface area rather than a variation on an existing module.

insert into permissions (resource, action, scope, description)
select resource, action, scope, initcap(replace(resource, '_', ' ')) || ': ' || initcap(action) || ' (' || initcap(scope) || ' scope)'
from (values ('COURSE'), ('CERTIFICATION')) as r(resource)
cross join (values ('CREATE'), ('READ'), ('UPDATE'), ('DELETE')) as a(action)
cross join (values ('TEAM'), ('DEPARTMENT'), ('ORGANIZATION')) as s(scope);

insert into permissions (resource, action, scope, description)
select resource, action, scope, initcap(replace(resource, '_', ' ')) || ': ' || initcap(action) || ' (' || initcap(scope) || ' scope)'
from (values ('COURSE_ENROLLMENT'), ('USER_CERTIFICATION')) as r(resource)
cross join (values ('CREATE'), ('READ'), ('UPDATE'), ('DELETE')) as a(action)
cross join (values ('OWN'), ('TEAM'), ('DEPARTMENT'), ('ORGANIZATION')) as s(scope);

create table courses (
    id                     uuid primary key default gen_random_uuid(),
    created_at             timestamptz not null default now(),
    updated_at             timestamptz not null default now(),
    created_by             uuid,
    updated_by             uuid,
    version                bigint not null default 0,
    organization_id        uuid not null references organizations (id),
    title                  varchar(200) not null,
    description            varchar(2000),
    category               varchar(20) not null,
    duration_minutes       integer not null default 0,
    passing_score_percent  integer not null default 70,
    active                 boolean not null default true,
    deleted_at             timestamptz
);

create index idx_courses_organization_id on courses (organization_id);
create index idx_courses_category on courses (organization_id, category);

create table course_enrollments (
    id                    uuid primary key default gen_random_uuid(),
    created_at            timestamptz not null default now(),
    updated_at            timestamptz not null default now(),
    created_by            uuid,
    updated_by            uuid,
    version               bigint not null default 0,
    organization_id       uuid not null references organizations (id),
    course_id             uuid not null references courses (id),
    -- The enrolled learner - this column plays the same "owner_id" role ScopeAuthorizationService
    -- checks against for every other owner-scoped resource, just named for what it actually means
    -- here (see CourseEnrollment's javadoc).
    user_id               uuid not null references users (id),
    -- Null when the learner self-enrolled; set when a manager/admin assigned the course to them -
    -- CourseEnrollmentService#create's ownerId-defaulting logic mirrors TicketService#resolveOwner
    -- for who's allowed to set this to someone other than themselves.
    assigned_by_user_id   uuid references users (id),
    status                varchar(20) not null default 'NOT_STARTED',
    score_percent         integer,
    due_date              date,
    started_at            timestamptz,
    completed_at          timestamptz,
    deleted_at            timestamptz
);

create index idx_course_enrollments_organization_id on course_enrollments (organization_id);
create index idx_course_enrollments_course_id on course_enrollments (organization_id, course_id);
create index idx_course_enrollments_user_id on course_enrollments (organization_id, user_id);
-- A learner can only have one *active* enrollment per course at a time - re-enrolling after a soft
-- delete (or after failing and being re-assigned) is allowed, but two simultaneously-live rows for
-- the same (course, user) would just be ambiguous about which one reflects real progress.
create unique index uq_course_enrollments_course_user_active
    on course_enrollments (organization_id, course_id, user_id) where deleted_at is null;

create table certifications (
    id                uuid primary key default gen_random_uuid(),
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now(),
    created_by        uuid,
    updated_by        uuid,
    version           bigint not null default 0,
    organization_id   uuid not null references organizations (id),
    name              varchar(200) not null,
    issuing_body      varchar(200),
    description       varchar(2000),
    -- Null means the credential never expires (e.g. a one-time onboarding badge) - see
    -- UserCertificationService#computeExpiresAt for how this feeds a new award's expiresAt.
    validity_months   integer,
    active            boolean not null default true,
    deleted_at        timestamptz
);

create index idx_certifications_organization_id on certifications (organization_id);

create table user_certifications (
    id                  uuid primary key default gen_random_uuid(),
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now(),
    created_by          uuid,
    updated_by          uuid,
    version             bigint not null default 0,
    organization_id     uuid not null references organizations (id),
    certification_id    uuid not null references certifications (id),
    user_id             uuid not null references users (id),
    credential_number   varchar(100),
    earned_at           date not null,
    -- Derived once at award time from certifications.validity_months (see
    -- UserCertificationService#computeExpiresAt) rather than recomputed on every read - a later
    -- change to a Certification's validity_months intentionally does not retroactively shift
    -- already-issued credentials' expiry, the same "snapshot what was true at the time" reasoning
    -- CommissionRecord's own stored rate/amount columns document for a plan that might change later.
    expires_at          date,
    status              varchar(20) not null default 'ACTIVE',
    notes               varchar(1000),
    deleted_at          timestamptz
);

create index idx_user_certifications_organization_id on user_certifications (organization_id);
create index idx_user_certifications_certification_id on user_certifications (organization_id, certification_id);
create index idx_user_certifications_user_id on user_certifications (organization_id, user_id);
