-- Time-Off Requests: staff requesting vacation/sick/personal leave, and a manager approving or
-- denying it. Nothing existing models this - Shift (V45) is when someone IS expected to work,
-- not a request to be excused from work, and there's no leave-request concept anywhere else.
--
-- Single owner-scoped entity, same shape as ClientGoal/Referral/ClientDocument: full OWN/TEAM/
-- DEPARTMENT/ORGANIZATION ladder, so TIME_OFF_REQUEST joins RoleService#isCoreCrmResource.
-- ownerId is the employee the request is FOR - unlike ClientGoal's owner/contact split, here the
-- request genuinely belongs to the person making it, so there's no separate "target" field.
-- status is a free state machine (PENDING/APPROVED/DENIED/CANCELLED) like every other lifecycle
-- field in this platform - approving a previously-denied request is a legitimate correction.
-- approved_at is stamped once, the first time status moves to APPROVED, and never overwritten
-- afterward, same "stamp once" rule shifts.clock_in_at/purchase_orders.received_at establish.

insert into permissions (resource, action, scope, description)
select resource, action, scope, initcap(replace(resource, '_', ' ')) || ': ' || initcap(action) || ' (' || initcap(scope) || ' scope)'
from (values ('TIME_OFF_REQUEST')) as r(resource)
cross join (values ('CREATE'), ('READ'), ('UPDATE'), ('DELETE')) as a(action)
cross join (values ('OWN'), ('TEAM'), ('DEPARTMENT'), ('ORGANIZATION')) as s(scope);

create table time_off_requests (
    id                uuid primary key default gen_random_uuid(),
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now(),
    created_by        uuid,
    updated_by        uuid,
    version           bigint not null default 0,
    organization_id   uuid not null references organizations (id),
    owner_id          uuid not null references users (id),
    start_date        date not null,
    end_date          date not null,
    type              varchar(20) not null default 'VACATION',
    status            varchar(20) not null default 'PENDING',
    approved_at       timestamptz,
    reason            varchar(2000),
    notes             varchar(2000),
    deleted_at        timestamptz
);

create index idx_time_off_requests_organization_id on time_off_requests (organization_id);
create index idx_time_off_requests_owner_id on time_off_requests (organization_id, owner_id);
