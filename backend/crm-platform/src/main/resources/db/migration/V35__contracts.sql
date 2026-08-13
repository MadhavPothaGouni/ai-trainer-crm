-- Contracts: the ongoing legal/subscription relationship with a customer, tracked after a deal
-- closes. Fills a real, currently-missing gap - Quote is a pre-close proposal (V5), Order is a
-- point-in-time transaction (V8), Invoice is a bill (V8); none of the three track "what did we
-- agree to, for how long, and does it auto-renew." A rep needs this to know when to start a
-- renewal conversation, and support/success needs it to know what's currently in force.
--
-- Owner-scoped CRM-resource pattern, mirrors tickets (V14) and booking_links (V33) exactly:
-- CONTRACT gets the full OWN/TEAM/DEPARTMENT/ORGANIZATION ladder (no EXPORT/IMPORT/ASSIGN -
-- same restrained action set booking_links/course_enrollments/sequence_enrollments use, not
-- the older full ticket/quote/lead set), joins RoleService#isCoreCrmResource so a default
-- MEMBER gets OWN+TEAM CREATE/READ/UPDATE, and ContractService#resolveOwner defaults a null
-- owner_id to the caller the same way TicketService/BookingLinkService already do.
--
-- account_id is required (a contract is always with a specific customer) and opportunity_id is
-- nullable (a renewal contract has no opportunity behind it; a fresh contract usually does) -
-- same nullable-FK-for-optional-link shape tickets.contact_id already established. Both are
-- real foreign keys, not the "plain uuid, no FK" convention used for references that must
-- survive their target being deleted (see V14's migration comment for that other shape).
--
-- status is a plain varchar(20) validated against a Java enum, same as every other status
-- column in this schema (tickets.status, orders.status, ...) - DRAFT -> ACTIVE is the normal
-- path, ACTIVE -> EXPIRED happens once end_date passes (no background sweep for this in this
-- pass, same restraint V32's migration comment documents for not building a real scheduler),
-- ACTIVE -> TERMINATED is an early exit, ACTIVE -> RENEWED means a follow-on contract now
-- covers this account and this row is historical. Like tickets.status (V14), this is
-- deliberately NOT enforced as a one-way state machine in ContractService - reopening a
-- terminated contract back to ACTIVE is a legitimate correction, not a bug.
--
-- total_value uses the same numeric(14, 2) shape quotes/orders/invoices already use (V5, V8).
-- renewal_term_months is nullable - only meaningful when auto_renew is true, not enforced at
-- the database level (validated in Java, same restraint macros.new_status's "no FK possible for
-- a Java enum" comment already takes for a conditionally-meaningful column).

insert into permissions (resource, action, scope, description)
select resource, action, scope, initcap(replace(resource, '_', ' ')) || ': ' || initcap(action) || ' (' || initcap(scope) || ' scope)'
from (values ('CONTRACT')) as r(resource)
cross join (values ('CREATE'), ('READ'), ('UPDATE'), ('DELETE')) as a(action)
cross join (values ('OWN'), ('TEAM'), ('DEPARTMENT'), ('ORGANIZATION')) as s(scope);

create table contracts (
    id                     uuid primary key default gen_random_uuid(),
    created_at             timestamptz not null default now(),
    updated_at             timestamptz not null default now(),
    created_by             uuid,
    updated_by             uuid,
    version                bigint not null default 0,
    organization_id        uuid not null references organizations (id),
    account_id             uuid not null references accounts (id),
    opportunity_id         uuid references opportunities (id),
    owner_id               uuid not null references users (id),
    contract_number        varchar(50) not null,
    title                  varchar(200) not null,
    status                 varchar(20) not null default 'DRAFT',
    start_date             date not null,
    end_date               date not null,
    total_value            numeric(14, 2) not null default 0,
    auto_renew             boolean not null default false,
    renewal_term_months    integer,
    signed_at              timestamptz,
    terms                  varchar(4000),
    deleted_at             timestamptz
);

create index idx_contracts_organization_id on contracts (organization_id);
create index idx_contracts_owner_id on contracts (organization_id, owner_id);
create index idx_contracts_account_id on contracts (account_id);
create index idx_contracts_status on contracts (organization_id, status);
-- Same "unique within an organization, not globally" shape uq_booking_links_org_slug (V33)
-- uses for slug - a contract number is human-assigned and only needs to be unambiguous inside
-- one tenant's data.
create unique index uq_contracts_org_number on contracts (organization_id, contract_number) where deleted_at is null;
