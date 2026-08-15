-- Locker & Locker Assignment: physical facility asset, complementing Equipment (V44) which is
-- training/maintenance gear, not client-facing storage. Nothing existing models this.
--
-- Two tables, the same catalog/occurrence split as Vendor/PurchaseOrder (V47): lockers is the
-- shared organization catalog of physical lockers, no owner_id, TEAM/DEPARTMENT/ORGANIZATION
-- only - a locker belongs to the facility, not one person. locker_assignments is the owner-scoped
-- occurrence: one client's assignment to a locker, full OWN/TEAM/DEPARTMENT/ORGANIZATION ladder,
-- so LOCKER_ASSIGNMENT (not LOCKER) joins RoleService#isCoreCrmResource.
--
-- status is a free state machine (ACTIVE/RETURNED/EXPIRED) like every other lifecycle field in
-- this platform. returned_at is stamped once, the first time status moves to RETURNED, and never
-- overwritten by a later correction - same "stamp once" rule purchase_orders.received_at (V47)
-- established.

insert into permissions (resource, action, scope, description)
select resource, action, scope, initcap(replace(resource, '_', ' ')) || ': ' || initcap(action) || ' (' || initcap(scope) || ' scope)'
from (values ('LOCKER')) as r(resource)
cross join (values ('CREATE'), ('READ'), ('UPDATE'), ('DELETE')) as a(action)
cross join (values ('TEAM'), ('DEPARTMENT'), ('ORGANIZATION')) as s(scope);

insert into permissions (resource, action, scope, description)
select resource, action, scope, initcap(replace(resource, '_', ' ')) || ': ' || initcap(action) || ' (' || initcap(scope) || ' scope)'
from (values ('LOCKER_ASSIGNMENT')) as r(resource)
cross join (values ('CREATE'), ('READ'), ('UPDATE'), ('DELETE')) as a(action)
cross join (values ('OWN'), ('TEAM'), ('DEPARTMENT'), ('ORGANIZATION')) as s(scope);

create table lockers (
    id                uuid primary key default gen_random_uuid(),
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now(),
    created_by        uuid,
    updated_by        uuid,
    version           bigint not null default 0,
    organization_id   uuid not null references organizations (id),
    label             varchar(50) not null,
    location          varchar(200),
    size              varchar(20) not null default 'MEDIUM',
    status            varchar(20) not null default 'ACTIVE',
    notes             varchar(2000),
    deleted_at        timestamptz
);

create index idx_lockers_organization_id on lockers (organization_id);

create table locker_assignments (
    id                uuid primary key default gen_random_uuid(),
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now(),
    created_by        uuid,
    updated_by        uuid,
    version           bigint not null default 0,
    organization_id   uuid not null references organizations (id),
    locker_id         uuid not null references lockers (id),
    contact_id        uuid not null references contacts (id),
    owner_id          uuid not null references users (id),
    assigned_at       timestamptz not null default now(),
    expires_at        date,
    status            varchar(20) not null default 'ACTIVE',
    returned_at       timestamptz,
    notes             varchar(2000),
    deleted_at        timestamptz
);

create index idx_locker_assignments_organization_id on locker_assignments (organization_id);
create index idx_locker_assignments_owner_id on locker_assignments (organization_id, owner_id);
create index idx_locker_assignments_locker_id on locker_assignments (locker_id);
create index idx_locker_assignments_contact_id on locker_assignments (contact_id);
