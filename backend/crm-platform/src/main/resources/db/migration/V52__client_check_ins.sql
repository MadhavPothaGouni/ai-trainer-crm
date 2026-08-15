-- Client Check-In: a facility access log entry - a client walking through the front door, distinct
-- from ClassAttendance (V43, tied to one specific class session) and TrainingSession (V37, tied to
-- one specific coaching appointment). This is the generic "who is in the building right now" fact,
-- the same way a front-desk kiosk or key-fob reader would record it.
--
-- Single owner-scoped entity, same shape as ClientGoal/Referral/TimeOffRequest: full
-- OWN/TEAM/DEPARTMENT/ORGANIZATION ladder. status is a free state machine (CHECKED_IN/CHECKED_OUT)
-- like every other lifecycle field in this platform. checked_in_at is set once at creation (the
-- fact of arrival never moves); checked_out_at is stamped once via PATCH .../status, the first
-- time status moves to CHECKED_OUT, same "stamp once" rule Shift#clockOutAt/PurchaseOrder#receivedAt
-- established.

insert into permissions (resource, action, scope, description)
select resource, action, scope, initcap(replace(resource, '_', ' ')) || ': ' || initcap(action) || ' (' || initcap(scope) || ' scope)'
from (values ('CLIENT_CHECK_IN')) as r(resource)
cross join (values ('CREATE'), ('READ'), ('UPDATE'), ('DELETE')) as a(action)
cross join (values ('OWN'), ('TEAM'), ('DEPARTMENT'), ('ORGANIZATION')) as s(scope);

create table client_check_ins (
    id                uuid primary key default gen_random_uuid(),
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now(),
    created_by        uuid,
    updated_by        uuid,
    version           bigint not null default 0,
    organization_id   uuid not null references organizations (id),
    contact_id        uuid not null references contacts (id),
    owner_id          uuid not null references users (id),
    checked_in_at     timestamptz not null default now(),
    status            varchar(20) not null default 'CHECKED_IN',
    checked_out_at    timestamptz,
    method            varchar(20) not null default 'MANUAL',
    notes             varchar(2000),
    deleted_at        timestamptz
);

create index idx_client_check_ins_organization_id on client_check_ins (organization_id);
create index idx_client_check_ins_owner_id on client_check_ins (organization_id, owner_id);
create index idx_client_check_ins_contact_id on client_check_ins (contact_id);
