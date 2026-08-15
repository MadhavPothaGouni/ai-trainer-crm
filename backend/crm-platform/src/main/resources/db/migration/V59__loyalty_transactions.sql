-- Loyalty Transaction: one entry in a client's points ledger - earned from a check-in or
-- referral, spent redeeming a reward, or a manual correction. points is a signed integer delta
-- (positive = earned, negative = spent); there's no separate balance column anywhere - a client's
-- current balance is always the live sum of their non-deleted transactions, computed on read (see
-- LoyaltyTransactionService#getBalance) rather than stored and risking drift.
--
-- Owner-scoped, full OWN/TEAM/DEPARTMENT/ORGANIZATION ladder, same contact_id-is-the-client /
-- owner_id-is-the-authorization-subject split every other contact-facing occurrence entity in this
-- platform uses. No status field, no PATCH .../status endpoint - a ledger entry is a point-in-time
-- fact, same shape as PromoRedemption/ProgressPhoto. reason constrains the sign of points
-- (EARNED_CHECKIN/EARNED_REFERRAL must be positive, REDEEMED_REWARD must be negative,
-- MANUAL_ADJUSTMENT can be either) - enforced in LoyaltyTransactionService#assertSignMatchesReason.

insert into permissions (resource, action, scope, description)
select resource, action, scope, initcap(replace(resource, '_', ' ')) || ': ' || initcap(action) || ' (' || initcap(scope) || ' scope)'
from (values ('LOYALTY_TRANSACTION')) as r(resource)
cross join (values ('CREATE'), ('READ'), ('UPDATE'), ('DELETE')) as a(action)
cross join (values ('OWN'), ('TEAM'), ('DEPARTMENT'), ('ORGANIZATION')) as s(scope);

create table loyalty_transactions (
    id                  uuid primary key default gen_random_uuid(),
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now(),
    created_by          uuid,
    updated_by          uuid,
    version             bigint not null default 0,
    organization_id     uuid not null references organizations (id),
    contact_id          uuid not null references contacts (id),
    owner_id            uuid not null references users (id),
    points              integer not null,
    reason              varchar(30) not null,
    notes               varchar(2000),
    deleted_at          timestamptz
);

create index idx_loyalty_transactions_organization_id on loyalty_transactions (organization_id);
create index idx_loyalty_transactions_owner_id on loyalty_transactions (organization_id, owner_id);
create index idx_loyalty_transactions_contact_id on loyalty_transactions (organization_id, contact_id);
