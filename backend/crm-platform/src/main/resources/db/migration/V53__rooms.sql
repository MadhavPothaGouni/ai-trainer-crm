-- Room & Room Booking: physical bookable spaces at the facility (a studio, a private training
-- room, a stretching area), distinct from Locker (V50, client storage) and Equipment (V44,
-- movable gear) - none of which model "a space you reserve for a block of time."
--
-- Same catalog/occurrence split as Vendor/PurchaseOrder (V47) and Locker/LockerAssignment (V50):
-- rooms is the shared organization catalog, no owner_id, TEAM/DEPARTMENT/ORGANIZATION only - a
-- room belongs to the facility, not one person. room_bookings is the owner-scoped occurrence:
-- one reservation of a room for a time window, full OWN/TEAM/DEPARTMENT/ORGANIZATION ladder, so
-- ROOM_BOOKING (not ROOM) joins RoleService#isCoreCrmResource.
--
-- status is a free state machine (CONFIRMED/CANCELLED) like every other lifecycle field in this
-- platform - re-confirming a cancelled booking is a legitimate correction, never blocked.
-- Unlike every prior occurrence entity, RoomBookingService also enforces a real business rule at
-- the application layer (not the database, since Postgres exclusion constraints on a soft-deleted,
-- status-filtered range aren't practical here): a room can't have two CONFIRMED bookings with
-- overlapping [starts_at, ends_at) windows. See RoomBookingService#assertNoOverlap.

insert into permissions (resource, action, scope, description)
select resource, action, scope, initcap(replace(resource, '_', ' ')) || ': ' || initcap(action) || ' (' || initcap(scope) || ' scope)'
from (values ('ROOM')) as r(resource)
cross join (values ('CREATE'), ('READ'), ('UPDATE'), ('DELETE')) as a(action)
cross join (values ('TEAM'), ('DEPARTMENT'), ('ORGANIZATION')) as s(scope);

insert into permissions (resource, action, scope, description)
select resource, action, scope, initcap(replace(resource, '_', ' ')) || ': ' || initcap(action) || ' (' || initcap(scope) || ' scope)'
from (values ('ROOM_BOOKING')) as r(resource)
cross join (values ('CREATE'), ('READ'), ('UPDATE'), ('DELETE')) as a(action)
cross join (values ('OWN'), ('TEAM'), ('DEPARTMENT'), ('ORGANIZATION')) as s(scope);

create table rooms (
    id                uuid primary key default gen_random_uuid(),
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now(),
    created_by        uuid,
    updated_by        uuid,
    version           bigint not null default 0,
    organization_id   uuid not null references organizations (id),
    label             varchar(50) not null,
    location          varchar(200),
    capacity          integer,
    status            varchar(20) not null default 'ACTIVE',
    notes             varchar(2000),
    deleted_at        timestamptz
);

create index idx_rooms_organization_id on rooms (organization_id);

create table room_bookings (
    id                uuid primary key default gen_random_uuid(),
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now(),
    created_by        uuid,
    updated_by        uuid,
    version           bigint not null default 0,
    organization_id   uuid not null references organizations (id),
    room_id           uuid not null references rooms (id),
    owner_id          uuid not null references users (id),
    purpose           varchar(200) not null,
    starts_at         timestamptz not null,
    ends_at           timestamptz not null,
    status            varchar(20) not null default 'CONFIRMED',
    notes             varchar(2000),
    deleted_at        timestamptz
);

create index idx_room_bookings_organization_id on room_bookings (organization_id);
create index idx_room_bookings_owner_id on room_bookings (organization_id, owner_id);
create index idx_room_bookings_room_id on room_bookings (room_id);
create index idx_room_bookings_room_id_status on room_bookings (room_id, status);
