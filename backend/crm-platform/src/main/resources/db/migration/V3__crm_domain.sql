-- The actual CRM domain: accounts (companies), contacts (people), opportunities
-- (the platform's term for what's commonly called a "deal" - matches
-- role.entity.Permission.Resource.OPPORTUNITY, seeded in V2), and leads
-- (unqualified prospects that convert into the other three).
--
-- Every foreign key to another CRM entity here (account_id, owner_id,
-- primary_contact_id, converted_*_id) is a plain uuid column, not a JPA
-- relationship on the entity side - same convention V1 already established
-- for users.manager_id/team_id. Keeping cross-entity references as ids
-- resolved explicitly in the service layer, rather than lazy-loaded JPA
-- associations, is deliberate: it's what already caused a real
-- LazyInitializationException bug on Role.permissions (see RoleRepository's
-- join-fetch comment) once this app disabled open-in-view, and it keeps
-- every one of these tables' access patterns identical and predictable.
--
-- Tables are ordered so every forward reference already exists: accounts,
-- then contacts (references accounts), then opportunities (references
-- accounts + contacts), then leads last (its convert_* columns can
-- reference all three).

-- ---------------------------------------------------------------------
-- Accounts (companies)
-- ---------------------------------------------------------------------

create table accounts (
    id                 uuid primary key default gen_random_uuid(),
    created_at         timestamptz not null default now(),
    updated_at         timestamptz not null default now(),
    created_by         uuid,
    updated_by         uuid,
    version            bigint not null default 0,
    organization_id    uuid not null references organizations (id),
    name               varchar(200) not null,
    industry           varchar(100),
    website            varchar(255),
    phone              varchar(30),
    billing_street     varchar(255),
    billing_city       varchar(100),
    billing_state      varchar(100),
    billing_postal_code varchar(20),
    billing_country    varchar(100),
    annual_revenue     numeric(15, 2),
    employee_count     integer,
    description        varchar(2000),
    owner_id           uuid not null references users (id),
    deleted_at         timestamptz
);

create index idx_accounts_organization_id on accounts (organization_id);
create index idx_accounts_owner_id on accounts (organization_id, owner_id);

-- ---------------------------------------------------------------------
-- Contacts (people, usually at an account)
-- ---------------------------------------------------------------------

create table contacts (
    id               uuid primary key default gen_random_uuid(),
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now(),
    created_by       uuid,
    updated_by       uuid,
    version          bigint not null default 0,
    organization_id  uuid not null references organizations (id),
    account_id       uuid references accounts (id),
    first_name       varchar(100) not null,
    last_name        varchar(100) not null,
    email            varchar(255),
    phone            varchar(30),
    title            varchar(150),
    description      varchar(2000),
    owner_id         uuid not null references users (id),
    deleted_at       timestamptz
);

create index idx_contacts_organization_id on contacts (organization_id);
create index idx_contacts_account_id on contacts (account_id);
create index idx_contacts_owner_id on contacts (organization_id, owner_id);

-- ---------------------------------------------------------------------
-- Opportunities ("deals" - a sales pipeline item tied to an account)
-- ---------------------------------------------------------------------

create table opportunities (
    id                    uuid primary key default gen_random_uuid(),
    created_at            timestamptz not null default now(),
    updated_at            timestamptz not null default now(),
    created_by            uuid,
    updated_by            uuid,
    version               bigint not null default 0,
    organization_id       uuid not null references organizations (id),
    account_id            uuid not null references accounts (id),
    primary_contact_id    uuid references contacts (id),
    name                  varchar(200) not null,
    stage                 varchar(30) not null default 'PROSPECTING',
    amount                numeric(14, 2),
    currency              varchar(3),
    expected_close_date   date,
    actual_close_date     date,
    description           varchar(2000),
    owner_id              uuid not null references users (id),
    deleted_at            timestamptz
);

create index idx_opportunities_organization_id on opportunities (organization_id);
create index idx_opportunities_account_id on opportunities (account_id);
create index idx_opportunities_owner_id on opportunities (organization_id, owner_id);
create index idx_opportunities_stage on opportunities (organization_id, stage);

-- ---------------------------------------------------------------------
-- Leads (unqualified prospects - convert into an account/contact/opportunity)
-- ---------------------------------------------------------------------

create table leads (
    id                        uuid primary key default gen_random_uuid(),
    created_at                timestamptz not null default now(),
    updated_at                timestamptz not null default now(),
    created_by                uuid,
    updated_by                uuid,
    version                   bigint not null default 0,
    organization_id           uuid not null references organizations (id),
    first_name                varchar(100) not null,
    last_name                 varchar(100) not null,
    email                     varchar(255),
    phone                     varchar(30),
    company_name              varchar(200),
    title                     varchar(150),
    status                    varchar(30) not null default 'NEW',
    source                    varchar(30) not null default 'OTHER',
    description               varchar(2000),
    owner_id                  uuid not null references users (id),
    converted_account_id      uuid references accounts (id),
    converted_contact_id      uuid references contacts (id),
    converted_opportunity_id  uuid references opportunities (id),
    converted_at              timestamptz,
    deleted_at                timestamptz
);

create index idx_leads_organization_id on leads (organization_id);
create index idx_leads_owner_id on leads (organization_id, owner_id);
create index idx_leads_status on leads (organization_id, status);
