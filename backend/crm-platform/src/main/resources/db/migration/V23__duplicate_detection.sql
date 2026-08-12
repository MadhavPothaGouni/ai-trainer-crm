-- Flags likely-duplicate Lead/Contact/Account pairs (matched by normalized email or name) and
-- records the outcome once a human reviews one: merged (with a chosen survivor) or dismissed as a
-- false positive. No permission is seeded here - a deliberate choice, not an oversight.
--
-- Every prior "reuse an existing permission" module this session (dashboard's extra permission
-- layered on REPORT:READ, sla's TicketSlaController checking TICKET:READ inline, forecast's
-- PipelineSnapshotController naming REPORT:READ directly) reused a permission for a READ-only
-- action. This module is the first to extend that reasoning to a WRITE action: merging two Leads
-- is exactly the kind of thing LEAD:UPDATE already gates (one record's data is being changed,
-- another is being deleted), so DuplicateMatchService checks LEAD/CONTACT/ACCOUNT's own UPDATE
-- permission - via ScopeAuthorizationService#assertCanAccess, the same record-level check
-- AccountService#update already makes - against BOTH records in the pair, not just one. A
-- dedicated DUPLICATE_MATCH permission would let someone merge two Leads they can't otherwise
-- touch, which is a real security gap a resource of its own would introduce, not avoid.
create table dedupe_matches (
    id             uuid primary key default gen_random_uuid(),
    created_at     timestamptz not null default now(),
    updated_at     timestamptz not null default now(),
    created_by     uuid,
    updated_by     uuid,
    version        bigint not null default 0,
    organization_id uuid not null references organizations (id),

    -- LEAD, CONTACT, or ACCOUNT - see DuplicateMatch's javadoc for why this is a closed set (the
    -- three CRM record types with a plausible "same real-world person/company, two rows" problem)
    -- rather than every resource in the permission catalog.
    entity_type    varchar(20) not null,

    -- Deliberately not "primary"/"duplicate" - DuplicateDetectionListener has no way to know which
    -- of two matching records a human will eventually want to keep, so record_a_id/record_b_id
    -- are just a normalized, order-independent pair (record_a is whichever of the two has the
    -- earlier created_at, tie-broken by id) purely so the same real-world pair is never flagged
    -- twice. survivor_id/absorbed_id (below) are the ones with real "which one won" meaning,
    -- populated only once a human actually merges the pair.
    record_a_id    uuid not null,
    record_b_id    uuid not null,

    -- EMAIL (an exact, case-insensitive match on the record's email field) or NAME (Account: exact
    -- normalized name; Lead: first+last+company; Contact: first+last, with no company field to
    -- scope it by, so NAME matches on a Contact are a weaker signal than an EMAIL match - see
    -- DuplicateDetectionListener's javadoc).
    match_reason   varchar(20) not null,

    status         varchar(20) not null default 'PENDING',
    survivor_id    uuid,
    absorbed_id    uuid,
    resolved_by_user_id uuid references users (id),
    resolved_at    timestamptz,

    constraint uq_dedupe_matches_org_type_pair unique (organization_id, entity_type, record_a_id, record_b_id)
);

-- The exact lookup a review queue runs: one organization, one entity type, PENDING first.
create index idx_dedupe_matches_lookup on dedupe_matches (organization_id, entity_type, status);
