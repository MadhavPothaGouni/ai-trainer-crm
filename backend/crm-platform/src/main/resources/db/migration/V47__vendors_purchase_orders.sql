-- Vendor & Purchase Order: who the organization buys supplies/equipment from, and the orders
-- placed with them. Nothing existing models this - Equipment (V44) is the asset once it's on
-- hand, not the purchase that acquired it, and there's no supplier concept anywhere else.
--
-- Two tables, the same catalog/occurrence split as Equipment/MaintenanceLog: vendors is the
-- shared organization catalog of suppliers, no owner_id, TEAM/DEPARTMENT/ORGANIZATION only - a
-- vendor relationship belongs to the org, not one person. purchase_orders is the owner-scoped
-- occurrence: one order placed with a vendor, full OWN/TEAM/DEPARTMENT/ORGANIZATION ladder, so
-- PURCHASE_ORDER (not VENDOR) joins RoleService#isCoreCrmResource.
--
-- status is a free state machine (DRAFT/ORDERED/RECEIVED/CANCELLED) like every other lifecycle
-- field in this platform. received_at is stamped once, the first time status moves to RECEIVED,
-- and never overwritten by a later correction - same "stamp once" rule shifts.clock_in_at/
-- clock_out_at established (see V45's migration comment).

insert into permissions (resource, action, scope, description)
select resource, action, scope, initcap(replace(resource, '_', ' ')) || ': ' || initcap(action) || ' (' || initcap(scope) || ' scope)'
from (values ('VENDOR')) as r(resource)
cross join (values ('CREATE'), ('READ'), ('UPDATE'), ('DELETE')) as a(action)
cross join (values ('TEAM'), ('DEPARTMENT'), ('ORGANIZATION')) as s(scope);

insert into permissions (resource, action, scope, description)
select resource, action, scope, initcap(replace(resource, '_', ' ')) || ': ' || initcap(action) || ' (' || initcap(scope) || ' scope)'
from (values ('PURCHASE_ORDER')) as r(resource)
cross join (values ('CREATE'), ('READ'), ('UPDATE'), ('DELETE')) as a(action)
cross join (values ('OWN'), ('TEAM'), ('DEPARTMENT'), ('ORGANIZATION')) as s(scope);

create table vendors (
    id                uuid primary key default gen_random_uuid(),
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now(),
    created_by        uuid,
    updated_by        uuid,
    version           bigint not null default 0,
    organization_id   uuid not null references organizations (id),
    name              varchar(200) not null,
    contact_name      varchar(200),
    email             varchar(255),
    phone             varchar(50),
    category          varchar(100),
    active            boolean not null default true,
    notes             varchar(2000),
    deleted_at        timestamptz
);

create index idx_vendors_organization_id on vendors (organization_id);

create table purchase_orders (
    id                       uuid primary key default gen_random_uuid(),
    created_at               timestamptz not null default now(),
    updated_at               timestamptz not null default now(),
    created_by               uuid,
    updated_by               uuid,
    version                  bigint not null default 0,
    organization_id          uuid not null references organizations (id),
    vendor_id                uuid not null references vendors (id),
    owner_id                 uuid not null references users (id),
    order_date               date not null,
    status                   varchar(20) not null default 'DRAFT',
    total_amount             numeric(14, 2),
    expected_delivery_date   date,
    received_at              timestamptz,
    notes                    varchar(2000),
    deleted_at               timestamptz
);

create index idx_purchase_orders_organization_id on purchase_orders (organization_id);
create index idx_purchase_orders_owner_id on purchase_orders (organization_id, owner_id);
create index idx_purchase_orders_vendor_id on purchase_orders (vendor_id);
