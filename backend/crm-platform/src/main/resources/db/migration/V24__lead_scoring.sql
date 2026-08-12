-- Rule-based Lead scoring: admin-defined LeadScoringRule rows (field/operator/
-- value/points, the same match-criterion shape territory_rules established
-- in V21) each contribute points to a Lead whenever their criterion matches
-- it; LeadScoringEngine sums every ACTIVE rule that matches and writes the
-- total onto leads.score.
--
-- Two deliberate differences from territory_rules, both worth calling out
-- explicitly since it would be easy to assume this is just "TerritoryRule
-- again":
--
-- 1. Aggregation model. TerritoryRule is "first ACTIVE match wins, evaluated
--    in priority order" - exactly one rule ever fires per record, because
--    assigning an owner is a single decision. Lead scoring is cumulative:
--    every ACTIVE rule whose criterion matches contributes its points, and
--    the Lead's score is their sum. There is no priority column here at all
--    - order never affects the result, unlike territory_rules.priority.
--
-- 2. Recompute trigger. TerritoryAssignmentListener and
--    DuplicateDetectionListener both deliberately fire once, on
--    RecordCreated only (see their own javadoc for why re-running against
--    an edited record is out of scope for those two features).
--    LeadScoringEngine listens to both RecordCreated AND RecordUpdated for
--    "Lead" - a Lead's source/company/title/email can genuinely change
--    after creation, and a stale score would be actively misleading for a
--    prioritization feature, unlike territory's one-time ownership
--    assignment or dedupe's one-time flagging.
--
-- match_field is Lead-only (SOURCE/COMPANY_NAME/TITLE/EMAIL_DOMAIN) - unlike
-- territory_rules.match_field, there's no second target_resource sharing
-- this table, so there's no field/resource pairing to validate at the
-- application level the way TerritoryRuleService#assertFieldValidForResource
-- does. EMAIL_DOMAIN matches against whatever follows '@' in Lead.email
-- (case-insensitive) - a signal territory_rules has no equivalent of.
--
-- LEAD_SCORING_RULE is admin configuration - CREATE/READ/UPDATE/DELETE at
-- ORGANIZATION scope only, the same third-kind shape SLA_POLICY (V20) and
-- TERRITORY_RULE (V21) already use.
insert into permissions (resource, action, scope, description)
select resource, action, 'ORGANIZATION', initcap(replace(resource, '_', ' ')) || ': ' || initcap(action) || ' (Organization scope)'
from (values ('LEAD_SCORING_RULE')) as r(resource)
cross join (values ('CREATE'), ('READ'), ('UPDATE'), ('DELETE')) as a(action);

create table lead_scoring_rules (
    id                uuid primary key default gen_random_uuid(),
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now(),
    created_by        uuid,
    updated_by        uuid,
    version           bigint not null default 0,
    organization_id   uuid not null references organizations (id),
    name              varchar(150) not null,
    match_field       varchar(30) not null,
    match_operator    varchar(20) not null,
    match_value       varchar(200) not null,
    points            int not null,
    active            boolean not null default true,
    match_count       int not null default 0,
    last_matched_at   timestamptz
);

-- The exact lookup LeadScoringEngine runs on every Lead create/update: active
-- rules for this org, cheapest-first. No field/operator narrowing here the
-- way idx_territory_rules_lookup has (target_resource) - every rule in this
-- table targets Lead, full stop.
create index idx_lead_scoring_rules_lookup on lead_scoring_rules (organization_id, active);

-- The computed, materialized total - not a live query, so a Lead list can
-- sort/filter on it cheaply. Signed (a rule's points can be negative, e.g.
-- "source = COLD_CALL: -10") so score itself can go below zero; there is no
-- floor at 0, since a very negative score is itself a meaningful signal
-- ("actively disqualified"), not noise to clamp away.
alter table leads add column score integer not null default 0;
create index idx_leads_org_score on leads (organization_id, score);
