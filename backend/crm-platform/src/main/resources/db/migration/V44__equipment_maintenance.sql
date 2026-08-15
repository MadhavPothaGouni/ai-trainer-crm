-- Equipment & Maintenance: gym/studio equipment inventory and its service history. Nothing in
-- the schema today tracks physical assets - Product (V5) is a sellable catalog item priced into
-- quotes, Attachment (V18) is a file, and neither models "a treadmill the org owns, which needs
-- periodic servicing and can go out of service."
--
-- Two tables, but a lighter-weight shape than the last several modules: neither table gets a
-- free-transition PATCH .../status endpoint. equipment's status changes through its normal PUT
-- (same as product.active), and maintenance_logs has no status field at all - it's a record of a
-- maintenance event that already happened, not a record with a lifecycle.
--
-- equipment is the shared organization catalog of physical assets - same TEAM/DEPARTMENT/
-- ORGANIZATION-only shape as PRODUCT/MEMBERSHIP_PLAN/GROUP_CLASS: no owner_id, since a piece of
-- equipment belongs to the org, not to one rep. status is nonetheless a free (non-linear) state
-- machine (ACTIVE/OUT_OF_SERVICE/RETIRED) - equipment coming back from repair, or a "retired"
-- unit getting reinstated after all, are both legitimate corrections.
--
-- maintenance_logs is the owner-scoped service history: one maintenance event performed by one
-- staff member on one piece of equipment. Full OWN/TEAM/DEPARTMENT/ORGANIZATION ladder like
-- Membership/ClientGoal, so MAINTENANCE_LOG (not EQUIPMENT) joins RoleService#isCoreCrmResource.
-- owner_id is who performed/logged the work (defaults to the caller via the usual resolveOwner
-- pattern). next_due_date is a plain nullable field the logger sets by hand - this module doesn't
-- attempt to auto-schedule follow-up maintenance, that's future scope, not something to fake here.

insert into permissions (resource, action, scope, description)
select resource, action, scope, initcap(replace(resource, '_', ' ')) || ': ' || initcap(action) || ' (' || initcap(scope) || ' scope)'
from (values ('EQUIPMENT')) as r(resource)
cross join (values ('CREATE'), ('READ'), ('UPDATE'), ('DELETE')) as a(action)
cross join (values ('TEAM'), ('DEPARTMENT'), ('ORGANIZATION')) as s(scope);

insert into permissions (resource, action, scope, description)
select resource, action, scope, initcap(replace(resource, '_', ' ')) || ': ' || initcap(action) || ' (' || initcap(scope) || ' scope)'
from (values ('MAINTENANCE_LOG')) as r(resource)
cross join (values ('CREATE'), ('READ'), ('UPDATE'), ('DELETE')) as a(action)
cross join (values ('OWN'), ('TEAM'), ('DEPARTMENT'), ('ORGANIZATION')) as s(scope);

create table equipment (
    id                uuid primary key default gen_random_uuid(),
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now(),
    created_by        uuid,
    updated_by        uuid,
    version           bigint not null default 0,
    organization_id   uuid not null references organizations (id),
    name              varchar(200) not null,
    category          varchar(100),
    serial_number     varchar(100),
    location          varchar(200),
    status            varchar(20) not null default 'ACTIVE',
    purchase_date     date,
    purchase_price    numeric(14, 2),
    notes             varchar(2000),
    deleted_at        timestamptz
);

create index idx_equipment_organization_id on equipment (organization_id);
create index idx_equipment_status on equipment (organization_id, status);

create table maintenance_logs (
    id                uuid primary key default gen_random_uuid(),
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now(),
    created_by        uuid,
    updated_by        uuid,
    version           bigint not null default 0,
    organization_id   uuid not null references organizations (id),
    equipment_id      uuid not null references equipment (id),
    owner_id          uuid not null references users (id),
    performed_at      timestamptz not null,
    type              varchar(20) not null default 'ROUTINE',
    cost              numeric(14, 2),
    notes             varchar(2000),
    next_due_date     date,
    deleted_at        timestamptz
);

create index idx_maintenance_logs_organization_id on maintenance_logs (organization_id);
create index idx_maintenance_logs_owner_id on maintenance_logs (organization_id, owner_id);
create index idx_maintenance_logs_equipment_id on maintenance_logs (equipment_id);
