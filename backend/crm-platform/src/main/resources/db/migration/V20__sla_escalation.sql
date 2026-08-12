-- SLA policies (response/resolution time targets per Ticket priority) and
-- automatic escalation when a ticket breaches one. Two tables:
--
-- sla_policies - admin-managed configuration: "URGENT tickets must get a
-- first response within 30 minutes and be resolved within 4 hours; if
-- either deadline passes, notify this manager." Org-wide administrative
-- concern, same reasoning V2's own comment gives for CUSTOM_FIELD/
-- API_KEY/INTEGRATION having no OWN/TEAM/DEPARTMENT variant - "what SLA
-- targets exist for this org" isn't a per-record ownership question, so
-- SLA_POLICY is seeded at ORGANIZATION scope only, same single-scope
-- pattern those resources use. At most one ACTIVE policy per (org,
-- priority) is enforced below via a partial unique index - SlaPolicyService
-- also pre-checks this and returns a clean 409 rather than letting the
-- constraint surface as a raw 500.
--
-- Which policy applies to a given ticket is deliberately not
-- retroactive: SlaEvaluationService computes a ticket's due dates once,
-- the first time it evaluates that ticket, and never recomputes them if
-- the matching policy's targets change afterward - a ticket already being
-- tracked keeps the deadlines it was given, the same way a Quote's
-- already-added line items don't reprice themselves when a Product's
-- unit_price changes later. Documented here as a deliberate simplification,
-- not an oversight.
insert into permissions (resource, action, scope, description)
select resource, action, 'ORGANIZATION', initcap(replace(resource, '_', ' ')) || ': ' || initcap(action) || ' (Organization scope)'
from (values ('SLA_POLICY')) as r(resource)
cross join (values ('CREATE'), ('READ'), ('UPDATE'), ('DELETE')) as a(action);

create table sla_policies (
    id                        uuid primary key default gen_random_uuid(),
    created_at                timestamptz not null default now(),
    updated_at                timestamptz not null default now(),
    created_by                uuid,
    updated_by                uuid,
    version                   bigint not null default 0,
    organization_id           uuid not null references organizations (id),
    name                      varchar(150) not null,
    priority                  varchar(20) not null,
    response_target_minutes   int not null,
    resolution_target_minutes int not null,
    escalate_to_user_id       uuid references users (id),
    active                    boolean not null default true
);

create index idx_sla_policies_organization_id on sla_policies (organization_id);

-- "At most one active policy per priority per org" - a partial index (only
-- covering rows where active = true) rather than a plain unique constraint
-- on (organization_id, priority), so an org can freely keep old, retired
-- policies around (active = false) for history without them colliding with
-- whatever policy is live now for that priority.
create unique index uq_sla_policies_org_priority_active on sla_policies (organization_id, priority) where active;

-- One row per ticket that has ever matched an active policy - created
-- lazily (find-or-create) by SlaEvaluationService#evaluate, not eagerly at
-- ticket-creation time. This table intentionally has no foreign key to
-- tickets: TicketService/Ticket itself is never touched by this migration
-- or this module - see SlaEvaluationService's javadoc for why staying
-- fully additive (reading Ticket's existing status/priority/resolved_at/
-- created_at, writing nothing back to it) was chosen over threading SLA
-- bookkeeping through the Ticket entity every other module that touches a
-- ticket would then have to know about.
--
-- response_breached_at / resolution_breached_at / escalated_at are all
-- nullable "first time this happened" timestamps, not booleans - once set
-- they're never cleared (even if the ticket is later reopened past its own
-- resolved_at getting cleared - see Ticket's own javadoc for why that one
-- is bidirectional). A breach that already happened stays on the record;
-- there's no "un-breaching" a deadline that already passed.
create table ticket_sla_statuses (
    id                     uuid primary key default gen_random_uuid(),
    created_at             timestamptz not null default now(),
    updated_at             timestamptz not null default now(),
    created_by             uuid,
    updated_by             uuid,
    version                bigint not null default 0,
    organization_id        uuid not null references organizations (id),
    ticket_id              uuid not null unique,
    sla_policy_id          uuid not null references sla_policies (id),
    response_due_at        timestamptz not null,
    resolution_due_at      timestamptz not null,
    response_breached_at   timestamptz,
    resolution_breached_at timestamptz,
    escalated_at           timestamptz
);

create index idx_ticket_sla_statuses_organization_id on ticket_sla_statuses (organization_id);
