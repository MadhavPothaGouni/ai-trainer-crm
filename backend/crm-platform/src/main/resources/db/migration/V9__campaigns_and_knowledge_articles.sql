-- Marketing/support tooling: Campaigns (with Campaign Members - a light
-- polymorphic link to either a Lead or a Contact, tracking that person's
-- engagement with the campaign) and the Knowledge Base (articles with tags
-- and a DRAFT -> PUBLISHED -> ARCHIVED lifecycle). Both CAMPAIGN and
-- KNOWLEDGE_ARTICLE were already seeded in V2 at TEAM/DEPARTMENT/
-- ORGANIZATION scope with CRUD + EXPORT (no OWN, no APPROVE) - the same
-- shared-org-resource pattern as PRODUCT/ORDER/INVOICE/PAYMENT, so neither
-- table below gets an owner_id column, and neither service calls
-- ScopeAuthorizationService.
--
-- campaign_members.campaign_id gets a real FK with cascade delete (same
-- reasoning as quote_line_items/order_line_items - a member only ever
-- belongs to one campaign). lead_id and contact_id are both nullable FKs
-- with NO cascade delete (deleting a lead/contact shouldn't silently wipe
-- campaign history) and a check constraint enforcing that exactly one of
-- the two is set - simpler than activities.related_to_id's fully polymorphic
-- "any of four tables, no FK at all" approach in V4, because a campaign
-- member is only ever a lead or a contact, never one of four types, so an
-- actual FK per column is possible here.
--
-- knowledge_article_tags is a simple element-collection join table (a set
-- of strings, not a full Tag entity with its own id/lifecycle - tags here
-- are just labels, not something with metadata worth managing separately).

create table campaigns (
    id                uuid primary key default gen_random_uuid(),
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now(),
    created_by        uuid,
    updated_by        uuid,
    version           bigint not null default 0,
    organization_id   uuid not null references organizations (id),
    name              varchar(200) not null,
    type              varchar(20) not null,
    status            varchar(20) not null default 'PLANNED',
    start_date        date,
    end_date          date,
    budget            numeric(14, 2),
    actual_cost       numeric(14, 2),
    description       varchar(2000),
    deleted_at        timestamptz
);

create index idx_campaigns_organization_id on campaigns (organization_id);

create table campaign_members (
    id             uuid primary key default gen_random_uuid(),
    created_at     timestamptz not null default now(),
    updated_at     timestamptz not null default now(),
    created_by     uuid,
    updated_by     uuid,
    version        bigint not null default 0,
    campaign_id    uuid not null references campaigns (id) on delete cascade,
    lead_id        uuid references leads (id),
    contact_id     uuid references contacts (id),
    status         varchar(20) not null default 'ADDED',
    responded_at   timestamptz,
    constraint chk_campaign_members_exactly_one_target
        check (((lead_id is not null)::int + (contact_id is not null)::int) = 1)
);

create index idx_campaign_members_campaign_id on campaign_members (campaign_id);
create unique index uq_campaign_members_campaign_lead on campaign_members (campaign_id, lead_id) where lead_id is not null;
create unique index uq_campaign_members_campaign_contact on campaign_members (campaign_id, contact_id) where contact_id is not null;

create table knowledge_articles (
    id                uuid primary key default gen_random_uuid(),
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now(),
    created_by        uuid,
    updated_by        uuid,
    version           bigint not null default 0,
    organization_id   uuid not null references organizations (id),
    title             varchar(300) not null,
    slug              varchar(320) not null,
    category          varchar(100),
    content           text not null,
    status            varchar(20) not null default 'DRAFT',
    view_count        integer not null default 0,
    published_at      timestamptz,
    deleted_at        timestamptz
);

create index idx_knowledge_articles_organization_id on knowledge_articles (organization_id);
create unique index uq_knowledge_articles_org_slug on knowledge_articles (organization_id, slug);
create index idx_knowledge_articles_category on knowledge_articles (organization_id, category);

create table knowledge_article_tags (
    knowledge_article_id   uuid not null references knowledge_articles (id) on delete cascade,
    tag                     varchar(50) not null,
    primary key (knowledge_article_id, tag)
);
