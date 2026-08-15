-- Progress Photo: a client's before/during/after physical-progress photo, logged at a point in
-- time. Distinct from ClientDocument (V49, signed paperwork) and BodyMeasurement (V37, numeric
-- readings) - this is specifically the visual record coaches use alongside those.
--
-- Single owner-scoped entity, same "point-in-time fact" shape PromoRedemption (V51) established:
-- no status field and no PATCH .../status endpoint, since a progress photo is a fact that was
-- taken, not something moving through states. takenAt is set once at creation, same as
-- PromoRedemption#redeemedAt. PROGRESS_PHOTO joins RoleService#isCoreCrmResource.

insert into permissions (resource, action, scope, description)
select resource, action, scope, initcap(replace(resource, '_', ' ')) || ': ' || initcap(action) || ' (' || initcap(scope) || ' scope)'
from (values ('PROGRESS_PHOTO')) as r(resource)
cross join (values ('CREATE'), ('READ'), ('UPDATE'), ('DELETE')) as a(action)
cross join (values ('OWN'), ('TEAM'), ('DEPARTMENT'), ('ORGANIZATION')) as s(scope);

create table progress_photos (
    id                uuid primary key default gen_random_uuid(),
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now(),
    created_by        uuid,
    updated_by        uuid,
    version           bigint not null default 0,
    organization_id   uuid not null references organizations (id),
    contact_id        uuid not null references contacts (id),
    owner_id          uuid not null references users (id),
    photo_url         varchar(1000) not null,
    category          varchar(20) not null default 'OTHER',
    taken_at          timestamptz not null default now(),
    notes             varchar(2000),
    deleted_at        timestamptz
);

create index idx_progress_photos_organization_id on progress_photos (organization_id);
create index idx_progress_photos_owner_id on progress_photos (organization_id, owner_id);
create index idx_progress_photos_contact_id on progress_photos (contact_id);
