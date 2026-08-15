-- Client Feedback: an NPS-style rating plus optional comments a client gave about one session,
-- class, or the business in general - see ClientFeedback's javadoc. Owner-scoped, no status field -
-- a submitted rating is a point-in-time fact, same shape as NutritionLog/ProgressPhoto.
-- related_type distinguishes what the feedback was about (SESSION/CLASS/GENERAL) without an FK,
-- since a single feedback record isn't required to reference a specific TrainingSession or
-- ClassSession row - "GENERAL" feedback about the business overall has nothing to point at.

insert into permissions (resource, action, scope, description)
select resource, action, scope, initcap(replace(resource, '_', ' ')) || ': ' || initcap(action) || ' (' || initcap(scope) || ' scope)'
from (values ('CLIENT_FEEDBACK')) as r(resource)
cross join (values ('CREATE'), ('READ'), ('UPDATE'), ('DELETE')) as a(action)
cross join (values ('OWN'), ('TEAM'), ('DEPARTMENT'), ('ORGANIZATION')) as s(scope);

create table client_feedback (
    id                  uuid primary key default gen_random_uuid(),
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now(),
    created_by          uuid,
    updated_by          uuid,
    version             bigint not null default 0,
    organization_id     uuid not null references organizations (id),
    contact_id          uuid not null references contacts (id),
    owner_id            uuid not null references users (id),
    nps_score           integer not null,
    related_type        varchar(20) not null default 'GENERAL',
    submitted_at        timestamptz not null default now(),
    comments            varchar(2000),
    deleted_at          timestamptz,
    constraint chk_client_feedback_nps_score check (nps_score between 0 and 10)
);

create index idx_client_feedback_organization_id on client_feedback (organization_id);
create index idx_client_feedback_owner_id on client_feedback (organization_id, owner_id);
create index idx_client_feedback_contact_id on client_feedback (contact_id);
