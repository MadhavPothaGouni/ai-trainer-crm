-- Sales tooling: a Product catalog and Quotes (a priced proposal tied to
-- exactly one Opportunity, made up of line items).
--
-- Products are deliberately NOT owner-scoped like accounts/contacts/
-- opportunities/leads/activities are - see role.entity.Permission.Resource
-- PRODUCT's seeded scopes in V2 (TEAM/DEPARTMENT/ORGANIZATION only, no OWN).
-- A product catalog is shared organization data, not something one rep
-- "owns," so there's no owner_id column and ProductService does no
-- record-level ScopeAuthorizationService check - holding any of the three
-- scopes for an action grants it against every product in the org.
--
-- Quotes, by contrast, are a normal owner-scoped CRM entity (QUOTE is in
-- RoleService#isCoreCrmResource and gets the full OWN/TEAM/DEPARTMENT/
-- ORGANIZATION treatment) - same pattern as accounts/contacts/opportunities/
-- leads/activities.
--
-- quote_line_items.quote_id gets a REAL foreign key with cascade delete,
-- unlike activities.related_to_id in V4 - a line item only ever belongs to
-- exactly one quote (not one-of-four types), so there's nothing polymorphic
-- to work around here. product_id has no cascade: products are soft-deleted
-- (deleted_at), never actually removed, so a line item's product reference
-- stays valid forever even after the product is deactivated.

create table products (
    id               uuid primary key default gen_random_uuid(),
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now(),
    created_by       uuid,
    updated_by       uuid,
    version          bigint not null default 0,
    organization_id  uuid not null references organizations (id),
    name             varchar(200) not null,
    sku              varchar(100),
    description      varchar(2000),
    unit_price       numeric(14, 2) not null default 0,
    currency         varchar(3),
    active           boolean not null default true,
    deleted_at       timestamptz
);

create index idx_products_organization_id on products (organization_id);
create index idx_products_sku on products (organization_id, sku);

create table quotes (
    id                uuid primary key default gen_random_uuid(),
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now(),
    created_by        uuid,
    updated_by        uuid,
    version           bigint not null default 0,
    organization_id   uuid not null references organizations (id),
    opportunity_id    uuid not null references opportunities (id),
    name              varchar(200) not null,
    status            varchar(20) not null default 'DRAFT',
    currency          varchar(3),
    valid_until       date,
    subtotal          numeric(14, 2) not null default 0,
    discount_amount   numeric(14, 2) not null default 0,
    tax_amount        numeric(14, 2) not null default 0,
    total_amount      numeric(14, 2) not null default 0,
    owner_id          uuid not null references users (id),
    deleted_at        timestamptz
);

create index idx_quotes_organization_id on quotes (organization_id);
create index idx_quotes_opportunity_id on quotes (organization_id, opportunity_id);
create index idx_quotes_owner_id on quotes (organization_id, owner_id);

create table quote_line_items (
    id           uuid primary key default gen_random_uuid(),
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now(),
    created_by   uuid,
    updated_by   uuid,
    version      bigint not null default 0,
    quote_id     uuid not null references quotes (id) on delete cascade,
    product_id   uuid references products (id),
    description  varchar(500) not null,
    quantity     integer not null default 1,
    unit_price   numeric(14, 2) not null default 0,
    line_total   numeric(14, 2) not null default 0
);

create index idx_quote_line_items_quote_id on quote_line_items (quote_id);
