-- Promo Code & Promo Redemption: discount/marketing codes clients redeem, and the record of each
-- redemption. Nothing existing models this - EmailTemplate/Campaign (V9) is outreach, not a
-- discount mechanism, and Order/Invoice (V8) have no notion of a coupon applied.
--
-- Two tables, the same catalog/occurrence split as Vendor/PurchaseOrder (V47) and Locker/
-- LockerAssignment (V50): promo_codes is the shared organization catalog of discount codes, no
-- owner_id, TEAM/DEPARTMENT/ORGANIZATION only - a promo code belongs to the org's marketing
-- program, not one person. promo_redemptions is the owner-scoped occurrence: one client's use of
-- a code, full OWN/TEAM/DEPARTMENT/ORGANIZATION ladder, so PROMO_REDEMPTION (not PROMO_CODE) joins
-- RoleService#isCoreCrmResource.
--
-- Unlike Shift/PurchaseOrder/LockerAssignment, a redemption has no status lifecycle - it's a
-- point-in-time fact (the code was redeemed, or it wasn't; there's no "pending redemption" to
-- transition through) closer to ClassAttendance's registered_at than to a free state machine, so
-- redeemed_at is simply set once at creation and the record otherwise behaves like a plain
-- owner-scoped fact table. order_id is an intentionally unenforced reference (no foreign key) to
-- keep this module decoupled from the sales/order module - a redemption can exist standalone
-- (e.g. redeemed for a membership signup) without requiring an order to already exist.

insert into permissions (resource, action, scope, description)
select resource, action, scope, initcap(replace(resource, '_', ' ')) || ': ' || initcap(action) || ' (' || initcap(scope) || ' scope)'
from (values ('PROMO_CODE')) as r(resource)
cross join (values ('CREATE'), ('READ'), ('UPDATE'), ('DELETE')) as a(action)
cross join (values ('TEAM'), ('DEPARTMENT'), ('ORGANIZATION')) as s(scope);

insert into permissions (resource, action, scope, description)
select resource, action, scope, initcap(replace(resource, '_', ' ')) || ': ' || initcap(action) || ' (' || initcap(scope) || ' scope)'
from (values ('PROMO_REDEMPTION')) as r(resource)
cross join (values ('CREATE'), ('READ'), ('UPDATE'), ('DELETE')) as a(action)
cross join (values ('OWN'), ('TEAM'), ('DEPARTMENT'), ('ORGANIZATION')) as s(scope);

create table promo_codes (
    id                  uuid primary key default gen_random_uuid(),
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now(),
    created_by          uuid,
    updated_by          uuid,
    version             bigint not null default 0,
    organization_id     uuid not null references organizations (id),
    code                varchar(50) not null,
    description         varchar(500),
    discount_type       varchar(20) not null default 'PERCENTAGE',
    discount_value      numeric(10, 2) not null,
    max_redemptions     integer,
    active              boolean not null default true,
    expires_at          date,
    notes               varchar(2000),
    deleted_at          timestamptz
);

create index idx_promo_codes_organization_id on promo_codes (organization_id);
create unique index uq_promo_codes_org_code on promo_codes (organization_id, code) where deleted_at is null;

create table promo_redemptions (
    id                    uuid primary key default gen_random_uuid(),
    created_at            timestamptz not null default now(),
    updated_at            timestamptz not null default now(),
    created_by            uuid,
    updated_by            uuid,
    version               bigint not null default 0,
    organization_id       uuid not null references organizations (id),
    promo_code_id         uuid not null references promo_codes (id),
    contact_id            uuid not null references contacts (id),
    owner_id              uuid not null references users (id),
    redeemed_at           timestamptz not null default now(),
    order_id              uuid,
    amount_discounted     numeric(14, 2),
    notes                 varchar(2000),
    deleted_at            timestamptz
);

create index idx_promo_redemptions_organization_id on promo_redemptions (organization_id);
create index idx_promo_redemptions_owner_id on promo_redemptions (organization_id, owner_id);
create index idx_promo_redemptions_promo_code_id on promo_redemptions (promo_code_id);
create index idx_promo_redemptions_contact_id on promo_redemptions (contact_id);
