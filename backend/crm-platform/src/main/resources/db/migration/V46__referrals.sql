-- Referral Program: a client refers someone they know, staff work the lead, and (optionally) a
-- reward gets issued once it converts into a paying client. Nothing existing models this - Lead
-- (V3) has no "referred by" concept and Contact has no referral-specific lifecycle.
--
-- Single owner-scoped entity, same shape as ClientGoal/Contract: full OWN/TEAM/DEPARTMENT/
-- ORGANIZATION ladder, so REFERRAL joins RoleService#isCoreCrmResource. referrer_contact_id
-- points at the existing client who made the referral; converted_contact_id is nullable and only
-- gets set once the referral actually becomes a Contact - stamped once, same "snapshot, don't let
-- it drift" rule as contracts.signed_at. reward_issued_at is likewise stamped once, independent of
-- reward_amount, since the amount can be edited (e.g. correcting a typo) without re-issuing.
--
-- status is a free state machine (PENDING/CONTACTED/CONVERTED/DECLINED) like every other
-- lifecycle field in this platform - moving a DECLINED referral back to CONTACTED is a legitimate
-- correction, not an invalid transition.

insert into permissions (resource, action, scope, description)
select resource, action, scope, initcap(replace(resource, '_', ' ')) || ': ' || initcap(action) || ' (' || initcap(scope) || ' scope)'
from (values ('REFERRAL')) as r(resource)
cross join (values ('CREATE'), ('READ'), ('UPDATE'), ('DELETE')) as a(action)
cross join (values ('OWN'), ('TEAM'), ('DEPARTMENT'), ('ORGANIZATION')) as s(scope);

create table referrals (
    id                     uuid primary key default gen_random_uuid(),
    created_at             timestamptz not null default now(),
    updated_at             timestamptz not null default now(),
    created_by             uuid,
    updated_by             uuid,
    version                bigint not null default 0,
    organization_id        uuid not null references organizations (id),
    referrer_contact_id    uuid not null references contacts (id),
    referred_name          varchar(200) not null,
    referred_email         varchar(255),
    referred_phone         varchar(50),
    owner_id               uuid not null references users (id),
    status                 varchar(20) not null default 'PENDING',
    converted_contact_id   uuid references contacts (id),
    reward_amount          numeric(10, 2),
    reward_issued_at       timestamptz,
    notes                  varchar(2000),
    deleted_at             timestamptz
);

create index idx_referrals_organization_id on referrals (organization_id);
create index idx_referrals_owner_id on referrals (organization_id, owner_id);
create index idx_referrals_referrer_contact_id on referrals (referrer_contact_id);
