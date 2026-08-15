-- Class Waitlist: a client queued for a spot in a full (or soon-to-be-full) ClassSession. Distinct
-- from ClassAttendance (V43), which tracks who's actually attending - a waitlist entry exists
-- specifically for clients who couldn't get a spot yet. Co-located in the groupclass package (not
-- a new classwaitlist package) so ClassWaitlistService can call ClassSessionService's
-- package-private findOrThrow when validating a new entry's parent session - same precedent
-- RoomBookingService established for Room.
--
-- Owner-scoped, full OWN/TEAM/DEPARTMENT/ORGANIZATION ladder, same contact_id-is-the-client /
-- owner_id-is-the-authorization-subject split every other contact-facing occurrence entity in this
-- platform uses. position is computed server-side at creation time (count of WAITING entries for
-- that session, plus one) rather than accepted from the client, so it can't be gamed or drift out
-- of order - see ClassWaitlistService#create. status is a free state machine (WAITING/NOTIFIED/
-- CONVERTED/EXPIRED), same restraint every other status machine in this platform documents.
-- notified_at is stamped once, the first time status moves to NOTIFIED, and never overwritten.

insert into permissions (resource, action, scope, description)
select resource, action, scope, initcap(replace(resource, '_', ' ')) || ': ' || initcap(action) || ' (' || initcap(scope) || ' scope)'
from (values ('CLASS_WAITLIST')) as r(resource)
cross join (values ('CREATE'), ('READ'), ('UPDATE'), ('DELETE')) as a(action)
cross join (values ('OWN'), ('TEAM'), ('DEPARTMENT'), ('ORGANIZATION')) as s(scope);

create table class_waitlists (
    id                  uuid primary key default gen_random_uuid(),
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now(),
    created_by          uuid,
    updated_by          uuid,
    version             bigint not null default 0,
    organization_id     uuid not null references organizations (id),
    class_session_id    uuid not null references class_sessions (id),
    contact_id          uuid not null references contacts (id),
    owner_id            uuid not null references users (id),
    position            integer not null,
    status              varchar(20) not null default 'WAITING',
    notified_at         timestamptz,
    notes               varchar(2000),
    deleted_at          timestamptz
);

create index idx_class_waitlists_organization_id on class_waitlists (organization_id);
create index idx_class_waitlists_owner_id on class_waitlists (organization_id, owner_id);
create index idx_class_waitlists_class_session_id on class_waitlists (class_session_id);
