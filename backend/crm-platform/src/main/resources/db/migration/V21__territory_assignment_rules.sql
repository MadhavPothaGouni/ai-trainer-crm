-- Rule-based auto-routing of newly created Leads/Accounts to an owner (or a
-- round-robin rotation across a Team's members) - "leads with source=WEBSITE
-- and company containing 'Corp' go to Priya" / "accounts billing out of
-- Germany get split evenly across the EMEA team."
--
-- A genuinely different concept from workflow/'s existing trigger/action
-- automation, not a duplicate of it: Workflow answers "when a Lead/Contact/
-- Account/Opportunity is CREATED/UPDATED/DELETED, create a task" - it never
-- touches who owns the record. TerritoryRule answers a narrower, different
-- question - "who should own this brand-new Lead/Account" - and it's the
-- one module this session where an @EventListener deliberately DOES write
-- back to another module's core ownerId column, not just create a new row
-- elsewhere the way WorkflowExecutionService (creates an Activity) and
-- SlaEvaluationService (creates a TicketSlaStatus row, never touches
-- tickets) both do. That's a deliberate, narrow exception: reassigning
-- ownership isn't a side effect here, it IS the feature, and it only ever
-- fires once, on RecordCreated, before a human has had any chance to
-- already claim or work the record - unlike reassigning an existing,
-- possibly-already-worked Ticket mid-flight (which SlaEvaluationService
-- explicitly avoids in favor of just notifying someone).
--
-- TERRITORY_RULE is admin configuration - CREATE/READ/UPDATE/DELETE at
-- ORGANIZATION scope only, same shape SLA_POLICY (V20) and CUSTOM_FIELD
-- already use for "what rules/policies exist" being an org-wide concern,
-- not a per-record ownership question.
insert into permissions (resource, action, scope, description)
select resource, action, 'ORGANIZATION', initcap(replace(resource, '_', ' ')) || ': ' || initcap(action) || ' (Organization scope)'
from (values ('TERRITORY_RULE')) as r(resource)
cross join (values ('CREATE'), ('READ'), ('UPDATE'), ('DELETE')) as a(action);

-- match_field/match_operator/match_value model a single (field, operator,
-- value) criterion rather than a full nested boolean expression tree (AND/OR
-- groups) - a deliberate, documented scope limit, the same "one criterion
-- per rule, add more rules instead of a rule builder" simplification a lot
-- of real assignment-rule products (Salesforce's Lead Assignment Rules
-- included) also start with. match_field is one of SOURCE/COMPANY_NAME
-- (valid only when target_resource = LEAD, matched against Lead.source/
-- Lead.companyName) or INDUSTRY/BILLING_COUNTRY/BILLING_STATE (valid only
-- when target_resource = ACCOUNT) - TerritoryRuleService validates the
-- field/resource pairing at write time, since there's no way to express
-- "these five varchars only make sense in these two groupings" as a single
-- CHECK constraint without a lot of SQL for little benefit over an
-- application-level check.
--
-- Exactly one of assign_to_user_id/assign_to_team_id is set (validated in
-- TerritoryRuleService, same "exactly one of two nullable FKs" shape
-- custom_fields.standard_entity_type/custom_object_id used first) -
-- assign_to_team_id round-robins across whichever users currently have
-- that team_id (see TerritoryAssignmentListener), tracked by
-- last_assigned_user_id as a cursor into that rotation. Rules are
-- evaluated in ascending priority order and the first ACTIVE rule whose
-- criterion matches wins - lower number runs first, same convention a
-- migration's own V-number ordering uses.
create table territory_rules (
    id                     uuid primary key default gen_random_uuid(),
    created_at             timestamptz not null default now(),
    updated_at             timestamptz not null default now(),
    created_by             uuid,
    updated_by             uuid,
    version                bigint not null default 0,
    organization_id        uuid not null references organizations (id),
    name                   varchar(150) not null,
    target_resource        varchar(20) not null,
    match_field            varchar(30) not null,
    match_operator         varchar(20) not null,
    match_value            varchar(200) not null,
    priority               int not null default 100,
    assign_to_user_id      uuid references users (id),
    assign_to_team_id      uuid references teams (id),
    last_assigned_user_id  uuid references users (id),
    active                 boolean not null default true,
    match_count            int not null default 0,
    last_matched_at        timestamptz
);

-- The exact lookup TerritoryAssignmentListener runs on every Lead/Account
-- creation: active rules for this org+resource, cheapest-first.
create index idx_territory_rules_lookup on territory_rules (organization_id, target_resource, active);
