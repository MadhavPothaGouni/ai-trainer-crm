-- Gift Card: a prepaid balance issued to a client, redeemed in whole or in part against future
-- purchases. Nothing existing models this - PromoCode (V51) is a discount rule, not a stored
-- balance, and Membership (V42) is a recurring plan, not a one-time prepaid amount.
--
-- Single owner-scoped entity, full OWN/TEAM/DEPARTMENT/ORGANIZATION ladder - unlike the
-- catalog/occurrence pairs of recent modules, a gift card has no separate "catalog" half; each
-- card is its own record with its own balance, closer to Contract (V35) or ClientGoal (V36) in
-- shape. GIFT_CARD joins RoleService#isCoreCrmResource.
--
-- status is a free state machine (ACTIVE/REDEEMED/EXPIRED/CANCELLED). Unlike every prior status
-- machine in this platform, redemption isn't just a status flip - it's a partial-or-full balance
-- deduction, so GiftCardService exposes a dedicated POST .../redeem endpoint (amount in the
-- request body) rather than only a PATCH .../status endpoint. current_balance starts equal to
-- initial_balance and only ever decreases via that endpoint; redeemed_at is stamped once, the
-- first time current_balance reaches zero (whether via one redemption or several), and never
-- overwritten afterward - same "stamp once" rule every other timestamp-on-transition field in
-- this platform follows.

insert into permissions (resource, action, scope, description)
select resource, action, scope, initcap(replace(resource, '_', ' ')) || ': ' || initcap(action) || ' (' || initcap(scope) || ' scope)'
from (values ('GIFT_CARD')) as r(resource)
cross join (values ('CREATE'), ('READ'), ('UPDATE'), ('DELETE')) as a(action)
cross join (values ('OWN'), ('TEAM'), ('DEPARTMENT'), ('ORGANIZATION')) as s(scope);

create table gift_cards (
    id                uuid primary key default gen_random_uuid(),
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now(),
    created_by        uuid,
    updated_by        uuid,
    version           bigint not null default 0,
    organization_id   uuid not null references organizations (id),
    contact_id        uuid not null references contacts (id),
    owner_id          uuid not null references users (id),
    code              varchar(50) not null,
    initial_balance   numeric(10, 2) not null,
    current_balance   numeric(10, 2) not null,
    status            varchar(20) not null default 'ACTIVE',
    issued_at         timestamptz not null default now(),
    expires_at        date,
    redeemed_at       timestamptz,
    notes             varchar(2000),
    deleted_at        timestamptz
);

create index idx_gift_cards_organization_id on gift_cards (organization_id);
create index idx_gift_cards_owner_id on gift_cards (organization_id, owner_id);
create index idx_gift_cards_contact_id on gift_cards (contact_id);
create unique index uq_gift_cards_org_code on gift_cards (organization_id, code) where deleted_at is null;
