-- Intake Form: the organization's catalog of intake questionnaires (new-client paperwork, PAR-Q
-- health screening, liability waivers, etc). Intake Form Submission: one client's completed
-- response to one of those forms. Same catalog-plus-occurrence pairing as Room/RoomBooking and
-- PromoCode/PromoRedemption, and for the same reason both live in this one migration and one
-- package (intakeform) - IntakeFormSubmissionService needs to validate a submission's parent form,
-- the same package-private findOrThrow reuse RoomBookingService established for Room.
--
-- IntakeForm is shared-organization-catalog shape (no owner_id, TEAM/DEPARTMENT/ORGANIZATION
-- scopes only), same as Room/Locker/PromoCode. IntakeFormSubmission is owner-scoped with the full
-- OWN/TEAM/DEPARTMENT/ORGANIZATION ladder, same contact_id-is-the-client / owner_id-is-the-
-- authorization-subject split every other contact-facing occurrence entity in this platform uses.
-- responses is a free-text blob (the client's answers, JSON-encoded by the frontend) rather than
-- structured columns - this platform has no per-form-type schema to validate against, so it's
-- stored opaquely, same simplification EmailMessage#bodyText already established for free-text
-- content this platform doesn't need to parse. No status field on the submission - a completed
-- intake form is a point-in-time fact, same shape as ProgressPhoto/PromoRedemption.

insert into permissions (resource, action, scope, description)
select resource, action, scope, initcap(replace(resource, '_', ' ')) || ': ' || initcap(action) || ' (' || initcap(scope) || ' scope)'
from (values ('INTAKE_FORM'), ('INTAKE_FORM_SUBMISSION')) as r(resource)
cross join (values ('CREATE'), ('READ'), ('UPDATE'), ('DELETE')) as a(action)
cross join (values ('OWN'), ('TEAM'), ('DEPARTMENT'), ('ORGANIZATION')) as s(scope);

create table intake_forms (
    id                  uuid primary key default gen_random_uuid(),
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now(),
    created_by          uuid,
    updated_by          uuid,
    version             bigint not null default 0,
    organization_id     uuid not null references organizations (id),
    title               varchar(200) not null,
    form_type           varchar(30) not null default 'OTHER',
    active              boolean not null default true,
    notes               varchar(2000),
    deleted_at          timestamptz
);

create index idx_intake_forms_organization_id on intake_forms (organization_id);

create table intake_form_submissions (
    id                  uuid primary key default gen_random_uuid(),
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now(),
    created_by          uuid,
    updated_by          uuid,
    version             bigint not null default 0,
    organization_id     uuid not null references organizations (id),
    form_id             uuid not null references intake_forms (id),
    contact_id          uuid not null references contacts (id),
    owner_id            uuid not null references users (id),
    submitted_at        timestamptz not null default now(),
    responses           text,
    notes               varchar(2000),
    deleted_at          timestamptz
);

create index idx_intake_form_submissions_organization_id on intake_form_submissions (organization_id);
create index idx_intake_form_submissions_owner_id on intake_form_submissions (organization_id, owner_id);
create index idx_intake_form_submissions_form_id on intake_form_submissions (form_id);
create index idx_intake_form_submissions_contact_id on intake_form_submissions (organization_id, contact_id);
