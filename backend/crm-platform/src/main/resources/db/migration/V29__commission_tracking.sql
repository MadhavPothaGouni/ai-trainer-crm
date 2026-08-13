-- Commission tracking: admin-defined CommissionPlan rows (a percentage-of-
-- amount or flat-per-deal rate, assigned to exactly one of an individual
-- user or a team - the same "exactly one of two" constraint sales_goals
-- (V25) and territory_rules/custom_fields established before it, again as
-- a real CHECK constraint) and CommissionRecord, a per-Opportunity snapshot
-- CommissionEngine creates the moment a deal closes CLOSED_WON.
--
-- CommissionRecord is deliberately MATERIALIZED, not computed live - the
-- opposite choice salesgoals/'s SalesGoalService made for goal progress,
-- and for a sharper reason than forecast/'s "a live query can't reconstruct
-- history" one: a commission is money owed. If CommissionPlan's rate
-- later changes, every commission already earned under the old rate must
-- keep its original amount forever - recomputing it live off the *current*
-- plan would silently change what a rep is owed for a deal they already
-- closed. So CommissionRecord snapshots rate_type/rate/commission_amount
-- at creation time and never touches the originating CommissionPlan again.
--
-- COMMISSION_PLAN is admin config - CREATE/READ/UPDATE/DELETE at
-- ORGANIZATION scope only, the same third-kind shape SLA_POLICY/
-- TERRITORY_RULE/LEAD_SCORING_RULE/SALES_GOAL/REGION already use.
-- COMMISSION_RECORD only gets READ and APPROVE - never CREATE/UPDATE/
-- DELETE, since the only writer is CommissionEngine and the only mutation
-- an API can ever make is the PENDING -> APPROVED -> PAID status walk
-- (APPROVE covers both forward steps) - the same "action set matches what's
-- actually possible" minimalism approval_requests' V19 comment documents
-- for why that resource skips EXPORT/ASSIGN.
insert into permissions (resource, action, scope, description)
select resource, action, 'ORGANIZATION', initcap(replace(resource, '_', ' ')) || ': ' || initcap(action) || ' (Organization scope)'
from (values ('COMMISSION_PLAN')) as r(resource)
cross join (values ('CREATE'), ('READ'), ('UPDATE'), ('DELETE')) as a(action)
union all
select resource, action, 'ORGANIZATION', initcap(replace(resource, '_', ' ')) || ': ' || initcap(action) || ' (Organization scope)'
from (values ('COMMISSION_RECORD')) as r(resource)
cross join (values ('READ'), ('APPROVE')) as a(action);

create table commission_plans (
    id                uuid primary key default gen_random_uuid(),
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now(),
    created_by        uuid,
    updated_by        uuid,
    version           bigint not null default 0,
    organization_id   uuid not null references organizations (id),
    name              varchar(150) not null,
    owner_user_id     uuid references users (id),
    team_id           uuid references teams (id),
    rate_type         varchar(20) not null,
    rate              numeric(10, 4) not null,
    active            boolean not null default true,

    constraint chk_commission_plans_exactly_one_target
        check (((owner_user_id is not null)::int + (team_id is not null)::int) = 1)
);

-- CommissionEngine's exact lookup order: an individual plan for this owner
-- first, falling back to a plan for the owner's current team only if no
-- individual plan exists - see CommissionEngine's javadoc for the full
-- resolution reasoning.
create index idx_commission_plans_owner on commission_plans (organization_id, owner_user_id) where active = true;
create index idx_commission_plans_team on commission_plans (organization_id, team_id) where active = true;

create table commission_records (
    id                  uuid primary key default gen_random_uuid(),
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now(),
    created_by          uuid,
    updated_by          uuid,
    version             bigint not null default 0,
    organization_id     uuid not null references organizations (id),
    opportunity_id      uuid not null references opportunities (id),
    owner_user_id       uuid not null references users (id),
    plan_id             uuid references commission_plans (id),
    deal_amount         numeric(14, 2) not null,
    rate_type           varchar(20) not null,
    rate                numeric(10, 4) not null,
    commission_amount   numeric(14, 2) not null,
    status              varchar(20) not null default 'PENDING',
    earned_at           timestamptz not null default now(),
    paid_at             timestamptz,

    -- An Opportunity can only ever generate one commission record - the
    -- real constraint backing CommissionEngine's idempotency check (it
    -- looks before it creates, but this is what actually prevents a race
    -- between two async listener invocations from ever double-paying a
    -- deal).
    constraint uq_commission_records_opportunity unique (opportunity_id)
);

create index idx_commission_records_organization_id on commission_records (organization_id);
-- CommissionRecordService#myRecords's exact lookup - a rep's own earned
-- commissions, newest first, the same self-scoped shape notification/'s
-- inbox and salesgoals/'s /mine endpoint already use.
create index idx_commission_records_owner on commission_records (organization_id, owner_user_id, earned_at desc);
