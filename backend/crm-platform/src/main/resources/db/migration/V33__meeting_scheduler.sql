-- Meeting scheduler (booking links): a rep publishes a reusable "book time with me" link
-- (BookingLink - title, duration, a slug) and hangs a set of open time slots off it
-- (BookingSlot). Booking a slot doesn't just flip a status flag - it creates a real
-- CalendarEvent (V15) through CalendarEventService, the same way Lead conversion creates a
-- real Opportunity and CommissionEngine reads a real Opportunity stage: the booking module
-- has no calendar logic of its own, it drives the existing one.
--
-- BookingLink mirrors Ticket's shape exactly: owner-scoped, full OWN/TEAM/DEPARTMENT/
-- ORGANIZATION permission ladder, in RoleService#isCoreCrmResource so MEMBER gets OWN+TEAM
-- by default, BookingLinkService#resolveOwner applies the identical null-defaults-to-caller/
-- ORGANIZATION-scope-to-assign-elsewhere rule TicketService#resolveOwner already does.
--
-- BookingSlot is a real FK child row (on delete cascade), no permission of its own - add/
-- remove is gated on the parent link's own UPDATE permission, the same shape SequenceStep
-- (V32) and QuoteLineItem (V6) already use. Unlike those two, a slot carries a real state
-- machine (OPEN -> BOOKED -> [nothing] / OPEN -> CANCELLED): BookingLinkService#book moves
-- OPEN -> BOOKED and stamps calendar_event_id with the CalendarEvent it just created;
-- BookingLinkService#cancel moves BOOKED -> CANCELLED and soft-deletes that same
-- CalendarEvent via CalendarEventService#delete - two systems, one action, kept honest by
-- actually calling the other module's service rather than duplicating its logic.
--
-- end_at is computed once at slot-creation time from the link's durationMinutes at that
-- moment and stored on the slot, never recomputed if the link's durationMinutes later
-- changes - the same "snapshot, don't let it drift" reasoning UserCertification#expiresAt
-- and CommissionRecord's frozen columns already document.
--
-- target_type/target_id (LEAD/CONTACT) are nullable until a slot is booked, then permanent
-- history - a cancelled slot keeps its target/bookedAt rather than being wiped back to a
-- blank OPEN slot, so "who was this meeting with" survives a cancellation. The unique
-- partial index below only blocks a *second* non-cancelled slot at the same instant on the
-- same link; a cancelled slot's time is free to be reused by a brand new slot row.

insert into permissions (resource, action, scope, description)
select resource, action, scope, initcap(replace(resource, '_', ' ')) || ': ' || initcap(action) || ' (' || initcap(scope) || ' scope)'
from (values ('BOOKING_LINK')) as r(resource)
cross join (values ('CREATE'), ('READ'), ('UPDATE'), ('DELETE')) as a(action)
cross join (values ('OWN'), ('TEAM'), ('DEPARTMENT'), ('ORGANIZATION')) as s(scope);

create table booking_links (
    id uuid primary key default gen_random_uuid(),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    created_by uuid,
    updated_by uuid,
    version bigint not null default 0,
    organization_id uuid not null references organizations (id),
    owner_id uuid not null references users (id),
    title varchar(200) not null,
    description varchar(2000),
    duration_minutes integer not null check (duration_minutes > 0),
    slug varchar(80) not null check (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    active boolean not null default true,
    deleted_at timestamptz
);
create index idx_booking_links_organization_id on booking_links (organization_id);
create index idx_booking_links_owner_id on booking_links (organization_id, owner_id);
create unique index uq_booking_links_org_slug on booking_links (organization_id, slug) where deleted_at is null;

create table booking_slots (
    id uuid primary key default gen_random_uuid(),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    created_by uuid,
    updated_by uuid,
    version bigint not null default 0,
    booking_link_id uuid not null references booking_links (id) on delete cascade,
    start_at timestamptz not null,
    end_at timestamptz not null,
    status varchar(20) not null default 'OPEN',
    target_type varchar(20),
    target_id uuid,
    booked_at timestamptz,
    calendar_event_id uuid references calendar_events (id),
    check (end_at > start_at),
    check ((target_type is null) = (target_id is null))
);
create index idx_booking_slots_booking_link_id on booking_slots (booking_link_id, start_at);
create unique index uq_booking_slots_link_start on booking_slots (booking_link_id, start_at) where status <> 'CANCELLED';
