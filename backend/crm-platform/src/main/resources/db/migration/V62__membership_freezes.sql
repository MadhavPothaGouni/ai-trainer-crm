-- Membership Freeze: a client pausing an active Membership for a date range (medical leave,
-- travel, etc.) without cancelling it outright. Co-located in the membership package (not a new
-- membershipfreeze package) so MembershipFreezeService can call MembershipService's
-- package-private findOrThrow when validating a freeze's parent membership - same precedent
-- RoomBookingService established for Room.
--
-- Owner-scoped, full OWN/TEAM/DEPARTMENT/ORGANIZATION ladder, same shape every other occurrence
-- entity in this platform uses. freeze_start/freeze_end are plain dates (not timestamps) since a
-- freeze always spans whole days. Two business rules, both checked at create/update time:
-- freeze_end must be after freeze_start (MembershipFreezeService#assertValidRange), and a
-- membership can't hold two REQUESTED/ACTIVE freezes with overlapping date ranges
-- (MembershipFreezeService#assertNoOverlap - the same overlap-checked-create pattern
-- RoomBookingService#assertNoOverlap established for Instant ranges, adapted here for LocalDate).
-- status is a free state machine (REQUESTED/ACTIVE/ENDED), same restraint every other status
-- machine in this platform documents.

insert into permissions (resource, action, scope, description)
select resource, action, scope, initcap(replace(resource, '_', ' ')) || ': ' || initcap(action) || ' (' || initcap(scope) || ' scope)'
from (values ('MEMBERSHIP_FREEZE')) as r(resource)
cross join (values ('CREATE'), ('READ'), ('UPDATE'), ('DELETE')) as a(action)
cross join (values ('OWN'), ('TEAM'), ('DEPARTMENT'), ('ORGANIZATION')) as s(scope);

create table membership_freezes (
    id                  uuid primary key default gen_random_uuid(),
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now(),
    created_by          uuid,
    updated_by          uuid,
    version             bigint not null default 0,
    organization_id     uuid not null references organizations (id),
    membership_id       uuid not null references memberships (id),
    owner_id            uuid not null references users (id),
    freeze_start        date not null,
    freeze_end          date not null,
    reason              varchar(500),
    status              varchar(20) not null default 'REQUESTED',
    notes               varchar(2000),
    deleted_at          timestamptz
);

create index idx_membership_freezes_organization_id on membership_freezes (organization_id);
create index idx_membership_freezes_owner_id on membership_freezes (organization_id, owner_id);
create index idx_membership_freezes_membership_id on membership_freezes (membership_id);
