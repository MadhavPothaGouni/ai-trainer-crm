-- Equipment Reservation: booking a specific piece of equipment (V44) for a time slot - e.g. a
-- client reserving the one squat rack for their session. Reuses the existing Equipment catalog
-- rather than introducing a new one; only the owner-scoped occurrence half is new here, so this
-- migration adds no new catalog table, just permissions and the reservation table itself.
--
-- Owner-scoped, full OWN/TEAM/DEPARTMENT/ORGANIZATION ladder, same shape as RoomBooking (V53)
-- minus the scheduling-conflict rule - unlike a bookable room, most equipment reservations in a
-- small studio are informal enough that double-booking detection isn't worth the complexity here.
-- status is a free state machine (CONFIRMED/CANCELLED). EQUIPMENT_RESERVATION joins
-- RoleService#isCoreCrmResource.

insert into permissions (resource, action, scope, description)
select resource, action, scope, initcap(replace(resource, '_', ' ')) || ': ' || initcap(action) || ' (' || initcap(scope) || ' scope)'
from (values ('EQUIPMENT_RESERVATION')) as r(resource)
cross join (values ('CREATE'), ('READ'), ('UPDATE'), ('DELETE')) as a(action)
cross join (values ('OWN'), ('TEAM'), ('DEPARTMENT'), ('ORGANIZATION')) as s(scope);

create table equipment_reservations (
    id                uuid primary key default gen_random_uuid(),
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now(),
    created_by        uuid,
    updated_by        uuid,
    version           bigint not null default 0,
    organization_id   uuid not null references organizations (id),
    equipment_id      uuid not null references equipment (id),
    contact_id        uuid references contacts (id),
    owner_id          uuid not null references users (id),
    starts_at         timestamptz not null,
    ends_at           timestamptz not null,
    status            varchar(20) not null default 'CONFIRMED',
    notes             varchar(2000),
    deleted_at        timestamptz
);

create index idx_equipment_reservations_organization_id on equipment_reservations (organization_id);
create index idx_equipment_reservations_owner_id on equipment_reservations (organization_id, owner_id);
create index idx_equipment_reservations_equipment_id on equipment_reservations (equipment_id);
