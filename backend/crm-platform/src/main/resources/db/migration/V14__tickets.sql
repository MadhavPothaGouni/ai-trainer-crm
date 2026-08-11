-- Support tickets - the one resource in role.entity.Permission.Resource /
-- V2__seed_permission_catalog.sql that had a full permission set (CREATE/
-- READ/UPDATE/DELETE/EXPORT/IMPORT/ASSIGN across OWN/TEAM/DEPARTMENT/
-- ORGANIZATION, the same "core CRM resource" group as LEAD/CONTACT/ACCOUNT/
-- OPPORTUNITY/ACTIVITY/QUOTE) but no table, entity, or endpoint anywhere in
-- the codebase - see backend/crm-platform/README.md's module layout section
-- for how that gap was found and why an earlier version of this file
-- incorrectly claimed every seeded resource had a module. This closes it.
--
-- Shape mirrors accounts/contacts/leads (V3) exactly on purpose - it's the
-- same owner-scoped, soft-deletable, four-scope-authorized pattern, just
-- with different business columns. account_id/contact_id are both nullable
-- (a ticket can be raised before either is on file, same reasoning
-- contacts.account_id being nullable already established) and both real
-- foreign keys, matching every other cross-CRM-entity reference in V3/V4 -
-- not the "plain uuid, no FK" convention used elsewhere in this schema for
-- references that deliberately survive their target being deleted
-- (workflow_runs.resource_id, custom_field_values.record_id,
-- activities.related_to_id) - a ticket's account/contact link has no such
-- requirement.
--
-- resolved_at is set when status moves to RESOLVED or CLOSED and cleared if
-- it moves back to OPEN/IN_PROGRESS (TicketService#updateStatus) - unlike
-- Lead's one-way CONVERTED or Order's linear DRAFT -> CONFIRMED ->
-- FULFILLED, ticket status is intentionally NOT a one-way state machine:
-- reopening a resolved ticket is a completely normal support workflow, so
-- there's no "invalid transition" business rule here at all, just plain
-- UPDATE-gated status changes.

create table tickets (
    id             uuid primary key default gen_random_uuid(),
    created_at     timestamptz not null default now(),
    updated_at     timestamptz not null default now(),
    created_by     uuid,
    updated_by     uuid,
    version        bigint not null default 0,
    organization_id uuid not null references organizations (id),
    account_id     uuid references accounts (id),
    contact_id     uuid references contacts (id),
    subject        varchar(200) not null,
    description    varchar(2000),
    status         varchar(20) not null default 'OPEN',
    priority       varchar(20) not null default 'MEDIUM',
    owner_id       uuid not null references users (id),
    resolved_at    timestamptz,
    deleted_at     timestamptz
);

create index idx_tickets_organization_id on tickets (organization_id);
create index idx_tickets_owner_id on tickets (organization_id, owner_id);
create index idx_tickets_account_id on tickets (account_id);
create index idx_tickets_status on tickets (organization_id, status);
