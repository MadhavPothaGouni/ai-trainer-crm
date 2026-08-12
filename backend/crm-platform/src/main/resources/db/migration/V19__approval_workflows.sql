-- Multi-step approval chains ("get sign-off before we send this $80k
-- quote") over a Quote/Order/Opportunity - a genuinely different concept
-- from Order/Invoice's existing single-permission-gated status transitions
-- (ORDER:APPROVE on DRAFT->CONFIRMED, INVOICE:APPROVE on DRAFT->SENT, both
-- from V8). Those two answer "does this one caller hold the right
-- permission to flip this status." An ApprovalRequest answers a different
-- question entirely: "did these N specific named people, in this specific
-- order, each say yes" - an ad-hoc process layered on top of a record,
-- not a role-gated state machine transition owned by the record itself.
-- The two mechanisms coexist without conflict: nothing stops an
-- organization from both requiring ORDER:APPROVE to confirm an order AND
-- attaching an ApprovalRequest to the same order for an extra round of
-- named sign-off first.
--
-- ApprovalRequest is a new resource - seeded and given a module in this
-- one migration, same as EMAIL_MESSAGE/CALENDAR_EVENT (V15)/TEAM
-- (V16)/ATTACHMENT (V18) before it. Its action set is deliberately small:
-- CREATE (submit)/READ/UPDATE (cancel your own pending request)/APPROVE
-- (act - approve or reject - on a step you're named on). No EXPORT/IMPORT
-- (an ad-hoc approval chain isn't a bulk-CSV workflow), no ASSIGN (there's
-- no single "owner" to reassign - the named approvers are fixed once a
-- request is submitted, and reshuffling them mid-flight would invalidate
-- whatever's already been decided), no DELETE (CANCELLED is the terminal
-- "this is gone" state - see below for why there's no deleted_at either).
insert into permissions (resource, action, scope, description)
select resource, action, scope, initcap(replace(resource, '_', ' ')) || ': ' || initcap(action) || ' (' || initcap(scope) || ' scope)'
from (values ('APPROVAL_REQUEST')) as r(resource)
cross join (values ('CREATE'), ('READ'), ('UPDATE'), ('APPROVE')) as a(action)
cross join (values ('OWN'), ('TEAM'), ('DEPARTMENT'), ('ORGANIZATION')) as s(scope);

-- Owner-scoped off requested_by_user_id (the submitter) via
-- ScopeAuthorizationService, same OWN/TEAM/DEPARTMENT/ORGANIZATION
-- ladder every owner-scoped module uses - but with a documented carve-out
-- ApprovalService adds on top: whoever is named as the approver on any
-- step of a request can always read that one request and act on their own
-- step, regardless of what scope they hold on APPROVAL_REQUEST:READ/
-- APPROVE. Being asked to approve something isn't a "how far up the org
-- chart can you see" question - it's "were you personally named" - so a
-- MEMBER with only OWN-scope APPROVAL_REQUEST:READ can still see and act
-- on a request some other rep submitted, as long as they're one of the
-- named approvers. See ApprovalService's javadoc for the full reasoning;
-- this is the platform's fifth resource-access shape, distinct from
-- owner-scoped, shared-org-resource, self-scoped (Notification, V17), and
-- the report/platform-administration special cases.
--
-- No deleted_at, unlike every owner-scoped table before it - a request
-- that's done being useful is CANCELLED (by the requester) or reaches a
-- terminal APPROVED/REJECTED status on its own; there's no separate "soft-
-- delete this" operation the way you'd delete a Ticket or an Attachment,
-- so the status column already carries the "this is gone" meaning a
-- deleted_at column would otherwise exist to capture.
create table approval_requests (
    id                    uuid primary key default gen_random_uuid(),
    created_at            timestamptz not null default now(),
    updated_at            timestamptz not null default now(),
    created_by            uuid,
    updated_by            uuid,
    version               bigint not null default 0,
    organization_id       uuid not null references organizations (id),
    related_to_type       varchar(20) not null,
    related_to_id         uuid not null,
    requested_by_user_id  uuid not null references users (id),
    title                 varchar(300) not null,
    status                varchar(20) not null default 'PENDING',
    current_step_number   int not null default 1,
    decided_at            timestamptz
);

create index idx_approval_requests_organization_id on approval_requests (organization_id);
create index idx_approval_requests_requested_by on approval_requests (organization_id, requested_by_user_id);
create index idx_approval_requests_related_to on approval_requests (related_to_type, related_to_id);

-- organization_id is denormalized here (also reachable via a join to
-- approval_requests) purely so the "my pending approvals" query - the one
-- this whole table exists to serve, and the frontend's main approvals
-- inbox - can filter/index directly on (organization_id, approver_user_id,
-- status) without joining every time. ApprovalStepService keeps it in sync
-- at creation time; it's never independently updated afterward.
create table approval_steps (
    id                  uuid primary key default gen_random_uuid(),
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now(),
    created_by          uuid,
    updated_by          uuid,
    version             bigint not null default 0,
    organization_id     uuid not null references organizations (id),
    approval_request_id uuid not null references approval_requests (id) on delete cascade,
    step_number         int not null,
    approver_user_id    uuid not null references users (id),
    status              varchar(20) not null default 'PENDING',
    comment             varchar(1000),
    decided_at          timestamptz
);

create unique index uq_approval_steps_request_step on approval_steps (approval_request_id, step_number);
create index idx_approval_steps_approver on approval_steps (organization_id, approver_user_id, status);
