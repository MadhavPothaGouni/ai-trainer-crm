-- Order-to-cash: Orders (optionally converted from a Quote), Invoices
-- (generated from exactly one Order), and Payments (recorded against
-- exactly one Invoice). Closes the loop V5 opened with Products/Quotes:
-- Account -> Opportunity -> Quote -> Order -> Invoice -> Payment.
--
-- All three headers follow the same shared-org-resource pattern as
-- Product (see V5's comment) rather than the owner-scoped pattern used by
-- accounts/contacts/opportunities/leads/activities/quotes - there's no
-- owner_id column on any of them, and V2 already seeded ORDER/INVOICE/
-- PAYMENT permissions at TEAM/DEPARTMENT/ORGANIZATION scope only (no OWN),
-- plus an APPROVE action alongside the usual CRUD. That APPROVE action
-- backs two specific transitions here: confirming a DRAFT order
-- (OrderService#confirm) and issuing a DRAFT invoice (InvoiceService#issue)
-- - both are deliberately gated separately from plain UPDATE, the same way
-- a real finance workflow separates "edit this" from "sign off on this."
--
-- order_line_items.order_id and invoice_line_items.invoice_id get real
-- foreign keys with cascade delete, same reasoning as quote_line_items in
-- V5. invoices.order_id and payments.invoice_id are NOT cascading deletes
-- deliberately - deleting (soft-deleting, in practice; nothing in this
-- schema hard-deletes a header) an order shouldn't be able to silently
-- orphan financial records referencing it, and neither should an invoice
-- take its payment history down with it; both stay soft-deleted so their
-- payment/invoice trail remains queryable.
--
-- invoices.amount_paid and payments are deliberately separate: amount_paid
-- is a stamped rollup (recomputed by PaymentService any time a payment is
-- recorded or removed, same "stamped, not computed on every read" reasoning
-- as Quote/Order's own subtotal/total_amount) while the payments table is
-- the append-mostly ledger of individual payments backing that rollup.

create table orders (
    id               uuid primary key default gen_random_uuid(),
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now(),
    created_by       uuid,
    updated_by       uuid,
    version          bigint not null default 0,
    organization_id  uuid not null references organizations (id),
    quote_id         uuid references quotes (id),
    order_number     varchar(50) not null,
    status           varchar(20) not null default 'DRAFT',
    currency         varchar(3),
    subtotal         numeric(14, 2) not null default 0,
    discount_amount  numeric(14, 2) not null default 0,
    tax_amount       numeric(14, 2) not null default 0,
    total_amount     numeric(14, 2) not null default 0,
    deleted_at       timestamptz
);

create index idx_orders_organization_id on orders (organization_id);
create index idx_orders_quote_id on orders (organization_id, quote_id);
create index idx_orders_order_number on orders (organization_id, order_number);

create table order_line_items (
    id           uuid primary key default gen_random_uuid(),
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now(),
    created_by   uuid,
    updated_by   uuid,
    version      bigint not null default 0,
    order_id     uuid not null references orders (id) on delete cascade,
    product_id   uuid references products (id),
    description  varchar(500) not null,
    quantity     integer not null default 1,
    unit_price   numeric(14, 2) not null default 0,
    line_total   numeric(14, 2) not null default 0
);

create index idx_order_line_items_order_id on order_line_items (order_id);

create table invoices (
    id               uuid primary key default gen_random_uuid(),
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now(),
    created_by       uuid,
    updated_by       uuid,
    version          bigint not null default 0,
    organization_id  uuid not null references organizations (id),
    order_id         uuid not null references orders (id),
    invoice_number   varchar(50) not null,
    status           varchar(20) not null default 'DRAFT',
    currency         varchar(3),
    issue_date       date not null,
    due_date         date not null,
    subtotal         numeric(14, 2) not null default 0,
    discount_amount  numeric(14, 2) not null default 0,
    tax_amount       numeric(14, 2) not null default 0,
    total_amount     numeric(14, 2) not null default 0,
    amount_paid      numeric(14, 2) not null default 0,
    deleted_at       timestamptz
);

create index idx_invoices_organization_id on invoices (organization_id);
create index idx_invoices_order_id on invoices (organization_id, order_id);
create index idx_invoices_invoice_number on invoices (organization_id, invoice_number);

create table invoice_line_items (
    id           uuid primary key default gen_random_uuid(),
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now(),
    created_by   uuid,
    updated_by   uuid,
    version      bigint not null default 0,
    invoice_id   uuid not null references invoices (id) on delete cascade,
    product_id   uuid references products (id),
    description  varchar(500) not null,
    quantity     integer not null default 1,
    unit_price   numeric(14, 2) not null default 0,
    line_total   numeric(14, 2) not null default 0
);

create index idx_invoice_line_items_invoice_id on invoice_line_items (invoice_id);

create table payments (
    id               uuid primary key default gen_random_uuid(),
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now(),
    created_by       uuid,
    updated_by       uuid,
    version          bigint not null default 0,
    organization_id  uuid not null references organizations (id),
    invoice_id       uuid not null references invoices (id),
    amount           numeric(14, 2) not null,
    method           varchar(20) not null,
    reference        varchar(200),
    paid_at          timestamptz not null default now(),
    notes            varchar(1000),
    deleted_at       timestamptz
);

create index idx_payments_organization_id on payments (organization_id);
create index idx_payments_invoice_id on payments (organization_id, invoice_id);
