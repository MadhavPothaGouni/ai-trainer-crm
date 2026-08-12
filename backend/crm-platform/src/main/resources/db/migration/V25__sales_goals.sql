-- Admin-set revenue/deal-count quotas for a period, assigned to either one
-- user or one team (exactly one, the same "exactly one of two" shape
-- custom_fields.standard_entity_type/custom_object_id (V10) and
-- territory_rules.assign_to_user_id/assign_to_team_id (V21) both already
-- use) - never both. Unlike Forecast (V22), progress against a goal is
-- computed LIVE at read time from real Opportunity rows, never
-- materialized: a goal's period is almost always still open when someone
-- checks it, so there is nothing to snapshot yet, and once a period ends
-- its Opportunity rows don't change any more, so a live query stays cheap
-- and correct forever without needing a captured history the way pipeline
-- value (which changes daily for open deals) does.
--
-- SALES_GOAL is admin configuration - CREATE/READ/UPDATE/DELETE at
-- ORGANIZATION scope only, the same third-kind shape SLA_POLICY (V20),
-- TERRITORY_RULE (V21), and LEAD_SCORING_RULE (V24) already use, since
-- defining who owes what quota is a manager/admin action. But unlike any
-- of those three, this module has a second, narrower access pattern
-- alongside it: GET /api/v1/sales-goals/mine skips the permission system
-- entirely and just returns the caller's own assigned goals (individual or
-- via their current team), the same "no Permission.Resource, no
-- ScopeAuthorizationService, just recipientId == caller" shape
-- notification/ (V17) established - a rep should always be able to see
-- their own quota and progress without needing SALES_GOAL:READ:ORGANIZATION,
-- the same way a MEMBER doesn't need any special permission to read their
-- own notification inbox.
insert into permissions (resource, action, scope, description)
select resource, action, 'ORGANIZATION', initcap(replace(resource, '_', ' ')) || ': ' || initcap(action) || ' (Organization scope)'
from (values ('SALES_GOAL')) as r(resource)
cross join (values ('CREATE'), ('READ'), ('UPDATE'), ('DELETE')) as a(action);

create table sales_goals (
    id               uuid primary key default gen_random_uuid(),
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now(),
    created_by       uuid,
    updated_by       uuid,
    version          bigint not null default 0,
    organization_id  uuid not null references organizations (id),
    name             varchar(150) not null,
    owner_user_id    uuid references users (id),
    team_id          uuid references teams (id),
    metric           varchar(20) not null,
    target_value     numeric(14, 2) not null,
    period_start     date not null,
    period_end       date not null,

    constraint chk_sales_goals_exactly_one_target
        check (((owner_user_id is not null)::int + (team_id is not null)::int) = 1),
    constraint chk_sales_goals_period_order check (period_end >= period_start)
);

-- The exact lookup GET /sales-goals/mine runs: every goal individually assigned to this user,
-- plus every goal assigned to their current team.
create index idx_sales_goals_owner on sales_goals (organization_id, owner_user_id);
create index idx_sales_goals_team on sales_goals (organization_id, team_id);
