-- Client Documents / Waivers: liability waivers, medical clearances, photo releases, and other
-- signed paperwork tied to one client. Nothing existing models this - Contract (V35) is a
-- commercial/legal agreement about services and pricing, not a liability waiver or medical form,
-- and Attachment (V18) is a generic file with no document-type/signing lifecycle of its own.
--
-- Single owner-scoped entity, same shape as ClientGoal/Referral: full OWN/TEAM/DEPARTMENT/
-- ORGANIZATION ladder, so CLIENT_DOCUMENT joins RoleService#isCoreCrmResource. contact_id is the
-- client the document is FOR (never the authorization subject, same "owner and target are
-- different people" split ClientGoal#contactId already established). signed_at is stamped once,
-- the first time status moves to SIGNED, and never overwritten afterward - same "snapshot, don't
-- let it drift" rule as contracts.signed_at/referrals.reward_issued_at. status is a free state
-- machine (PENDING/SIGNED/EXPIRED/REVOKED) like every other lifecycle field in this platform.

insert into permissions (resource, action, scope, description)
select resource, action, scope, initcap(replace(resource, '_', ' ')) || ': ' || initcap(action) || ' (' || initcap(scope) || ' scope)'
from (values ('CLIENT_DOCUMENT')) as r(resource)
cross join (values ('CREATE'), ('READ'), ('UPDATE'), ('DELETE')) as a(action)
cross join (values ('OWN'), ('TEAM'), ('DEPARTMENT'), ('ORGANIZATION')) as s(scope);

create table client_documents (
    id                uuid primary key default gen_random_uuid(),
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now(),
    created_by        uuid,
    updated_by        uuid,
    version           bigint not null default 0,
    organization_id   uuid not null references organizations (id),
    contact_id        uuid not null references contacts (id),
    owner_id          uuid not null references users (id),
    document_type     varchar(30) not null,
    title             varchar(200) not null,
    status            varchar(20) not null default 'PENDING',
    signed_at         timestamptz,
    expires_at        date,
    file_url          varchar(2000),
    notes             varchar(2000),
    deleted_at        timestamptz
);

create index idx_client_documents_organization_id on client_documents (organization_id);
create index idx_client_documents_owner_id on client_documents (organization_id, owner_id);
create index idx_client_documents_contact_id on client_documents (contact_id);
