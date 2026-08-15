-- Membership & Subscription Plans: recurring billing relationships between an organization and
-- its clients, distinct from every existing sales entity. Order/Invoice/Payment (V8) model a
-- ONE-OFF sale; Contract (V35) models legal/subscription TERMS without any billing-cycle
-- concept of its own; SalesGoal (V25) is an internal rep's quota, never a customer-facing
-- record. Nothing in the schema tracks "this client pays on a recurring cadence for ongoing
-- access," which is exactly what a gym/training membership is.
--
-- Two tables, mirroring the Product/Quote split (V5) rather than inventing a new shape:
--
-- membership_plans is the shared organization CATALOG (Unlimited Monthly, 10-Session Pack,
-- Annual Elite, ...) - same reasoning as PRODUCT's seeded scopes: TEAM/DEPARTMENT/ORGANIZATION
-- only, no OWN, no owner_id column. A plan catalog is shared organization data, not something
-- one rep "owns," so MembershipService does no record-level ScopeAuthorizationService check on
-- plans - holding any of the three scopes for an action grants it against every plan in the org.
--
-- memberships is the normal owner-scoped CRM entity: one Contact's actual subscription to a
-- plan, full OWN/TEAM/DEPARTMENT/ORGANIZATION ladder like Contract/ClientGoal, so MEMBERSHIP
-- (not MEMBERSHIP_PLAN) joins RoleService#isCoreCrmResource. contact_id is a real FK (the
-- client the membership is FOR); membership_plan_id is a real FK to the org's catalog, never
-- nullable - a membership is always "of" some plan, the same way a quote_line_item always
-- references a product.
--
-- status is a free (non-linear) state machine like tickets.status/contracts.status/
-- client_goals.status - reactivating a paused or cancelled membership is a legitimate everyday
-- action (a client returning after a break), not an invalid transition. billing_cycle_price is
-- snapshotted onto the membership at creation time from the plan's current price, the same
-- "don't let it drift" reasoning quote_line_items.unit_price already documents for products -
-- a plan's list price changing later must never retroactively change what an existing member
-- is being billed. next_billing_date is nullable (null once cancelled/expired, since there's
-- nothing left to bill), and paused_at/cancelled_at are stamped once and never cleared, the same
-- "snapshot" rule contracts.signed_at and client_goals.achieved_at already establish.

insert into permissions (resource, action, scope, description)
select resource, action, scope, initcap(replace(resource, '_', ' ')) || ': ' || initcap(action) || ' (' || initcap(scope) || ' scope)'
from (values ('MEMBERSHIP_PLAN')) as r(resource)
cross join (values ('CREATE'), ('READ'), ('UPDATE'), ('DELETE')) as a(action)
cross join (values ('TEAM'), ('DEPARTMENT'), ('ORGANIZATION')) as s(scope);

insert into permissions (resource, action, scope, description)
select resource, action, scope, initcap(replace(resource, '_', ' ')) || ': ' || initcap(action) || ' (' || initcap(scope) || ' scope)'
from (values ('MEMBERSHIP')) as r(resource)
cross join (values ('CREATE'), ('READ'), ('UPDATE'), ('DELETE')) as a(action)
cross join (values ('OWN'), ('TEAM'), ('DEPARTMENT'), ('ORGANIZATION')) as s(scope);

create table membership_plans (
    id                   uuid primary key default gen_random_uuid(),
    created_at           timestamptz not null default now(),
    updated_at           timestamptz not null default now(),
    created_by           uuid,
    updated_by           uuid,
    version              bigint not null default 0,
    organization_id      uuid not null references organizations (id),
    name                 varchar(200) not null,
    description          varchar(2000),
    billing_cycle        varchar(20) not null,
    price                numeric(14, 2) not null default 0,
    currency             varchar(3),
    session_credits      integer,
    active               boolean not null default true,
    deleted_at           timestamptz
);

create index idx_membership_plans_organization_id on membership_plans (organization_id);

create table memberships (
    id                    uuid primary key default gen_random_uuid(),
    created_at            timestamptz not null default now(),
    updated_at            timestamptz not null default now(),
    created_by            uuid,
    updated_by            uuid,
    version               bigint not null default 0,
    organization_id       uuid not null references organizations (id),
    contact_id            uuid not null references contacts (id),
    membership_plan_id    uuid not null references membership_plans (id),
    owner_id              uuid not null references users (id),
    status                varchar(20) not null default 'ACTIVE',
    billing_cycle_price   numeric(14, 2) not null default 0,
    start_date            date not null,
    end_date              date,
    next_billing_date     date,
    auto_renew            boolean not null default true,
    remaining_credits     integer,
    paused_at             timestamptz,
    cancelled_at          timestamptz,
    notes                 varchar(2000),
    deleted_at            timestamptz
);

create index idx_memberships_organization_id on memberships (organization_id);
create index idx_memberships_owner_id on memberships (organization_id, owner_id);
create index idx_memberships_contact_id on memberships (contact_id);
create index idx_memberships_status on memberships (organization_id, status);
create index idx_memberships_plan_id on memberships (membership_plan_id);
