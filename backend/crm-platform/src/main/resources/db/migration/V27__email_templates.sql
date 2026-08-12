-- Reusable, org-wide email templates with {{token}} placeholders, mail-merged
-- against a real Contact/Lead/Account/Opportunity by EmailTemplateService#render.
--
-- EMAIL_TEMPLATE is shared organization content, not something one rep owns -
-- the same reasoning products/'s V5 migration comment documents for PRODUCT:
-- CREATE/READ/UPDATE/DELETE are seeded here at TEAM/DEPARTMENT/ORGANIZATION
-- scope only (no OWN), and EmailTemplateService does no per-record
-- ScopeAuthorizationService check at all - holding any of the three scopes
-- grants that action against every template in the org. This is the second
-- time this exact "shared catalog, permission-gated-but-no-record-scope"
-- shape has appeared this session (see README's module layout for the full
-- writeup), so unlike LEAD_SCORING_RULE/TERRITORY_RULE/SALES_GOAL (the
-- "third kind": ORGANIZATION-only admin config), this one deliberately
-- mirrors PRODUCT's three-scope ladder instead of inventing a fresh shape.
insert into permissions (resource, action, scope, description)
select resource, action, scope, initcap(replace(resource, '_', ' ')) || ': ' || initcap(action) || ' (' || initcap(lower(scope)) || ' scope)'
from (values ('EMAIL_TEMPLATE')) as r(resource)
cross join (values ('CREATE'), ('READ'), ('UPDATE'), ('DELETE')) as a(action)
cross join (values ('TEAM'), ('DEPARTMENT'), ('ORGANIZATION')) as s(scope);

create table email_templates (
    id                uuid primary key default gen_random_uuid(),
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now(),
    created_by        uuid,
    updated_by        uuid,
    version           bigint not null default 0,
    organization_id   uuid not null references organizations (id),
    name              varchar(150) not null,
    category          varchar(20) not null default 'GENERAL',
    subject           varchar(300) not null,
    body              text not null,
    active            boolean not null default true,
    deleted_at        timestamptz
);

create index idx_email_templates_organization_id on email_templates (organization_id);

-- category is a plain filter, not a target_resource-style polymorphism switch
-- the way territory_rules/custom_fields use their "exactly one of two" target
-- columns - a template's placeholders are resolved generically at render time
-- (whatever {{contact.*}}/{{lead.*}}/{{account.*}}/{{opportunity.*}}/
-- {{sender.*}} tokens happen to appear in its subject/body), so nothing here
-- needs to declare up front which entity type a template is "for".
create index idx_email_templates_category on email_templates (organization_id, category);
