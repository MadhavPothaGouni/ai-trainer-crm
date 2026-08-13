-- A nested Region tree ("North America" contains "US-West"/"US-East") that
-- sits ABOVE territory/'s existing TerritoryRule, not on top of it - a
-- genuinely different concept, same "different question, not a duplicate"
-- reasoning territory_assignment_rules' own V21 comment used to distinguish
-- itself from workflow/. TerritoryRule answers "who should own this
-- brand-new Lead/Account" (a routing decision, fired once by an
-- @EventListener); Region answers "how does our sales org roll up for
-- reporting" (a static org-chart-shaped grouping of Teams, queried on
-- demand, never fired by anything). Nothing here reads or writes
-- TerritoryRule or its listener, and vice versa.
--
-- REGION is admin configuration - CREATE/READ/UPDATE/DELETE at ORGANIZATION
-- scope only, the same third-kind shape SLA_POLICY/TERRITORY_RULE/
-- LEAD_SCORING_RULE/SALES_GOAL already use: defining org structure is
-- admin work, not something scoped by OWN/TEAM/DEPARTMENT.
insert into permissions (resource, action, scope, description)
select resource, action, 'ORGANIZATION', initcap(replace(resource, '_', ' ')) || ': ' || initcap(action) || ' (Organization scope)'
from (values ('REGION')) as r(resource)
cross join (values ('CREATE'), ('READ'), ('UPDATE'), ('DELETE')) as a(action);

create table regions (
    id                 uuid primary key default gen_random_uuid(),
    created_at         timestamptz not null default now(),
    updated_at         timestamptz not null default now(),
    created_by         uuid,
    updated_by         uuid,
    version            bigint not null default 0,
    organization_id    uuid not null references organizations (id),
    name               varchar(150) not null,
    -- Self-referential, deliberately NOT a real foreign key back onto this
    -- same table: Postgres allows a self-FK just fine, but RegionService
    -- already has to walk the parent chain in application code to detect
    -- cycles (a plain FK can't express "no cycles" at all), so there is no
    -- extra integrity a DB-level self-FK would buy here that the service
    -- doesn't already have to guarantee itself - same reasoning
    -- territory_rules.target_resource's lack of a real FK documents for a
    -- different kind of polymorphism.
    parent_region_id   uuid,
    description        varchar(2000),
    deleted_at         timestamptz
);

create index idx_regions_organization_id on regions (organization_id);
-- RegionService#getDescendantRegionIds's exact access pattern: given an
-- org, find every region whose parent is X. Loaded once per rollup call
-- and walked in memory (see RegionService's javadoc) rather than a
-- recursive CTE - an org's region count is small enough that this is
-- simpler and just as fast, and it lets RegionService reuse the same
-- "resolve fresh on every read" reasoning SalesGoalService already applies
-- to team membership.
create index idx_regions_parent on regions (organization_id, parent_region_id);

-- A Team optionally belongs to exactly one Region - the join RegionService's
-- rollup uses to get from "this region and everything under it" to "these
-- users' Opportunities." Nullable: a team not yet assigned to the org chart
-- simply doesn't roll up anywhere, the same way a Lead with no matching
-- TerritoryRule just keeps its default owner.
alter table teams add column region_id uuid references regions (id);
create index idx_teams_region_id on teams (organization_id, region_id);
