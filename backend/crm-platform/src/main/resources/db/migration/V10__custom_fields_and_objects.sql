-- Platform extensibility: Custom Objects (admin-defined, Salesforce-style
-- generic entities - each one is just a table of "records" with a single
-- required Name, everything else about a record comes from whatever custom
-- fields are attached to it) and Custom Fields (admin-defined extra fields,
-- attachable to either one of a small allow-list of standard CRM entities -
-- ACCOUNT/CONTACT/LEAD/OPPORTUNITY/CAMPAIGN - or to a custom object).
--
-- CUSTOM_FIELD and CUSTOM_OBJECT were already seeded in V2 at ORGANIZATION
-- scope only, alongside USER/ROLE/ORGANIZATION/INTEGRATION/API_KEY - "define
-- what fields/objects exist" is inherently an org-wide administrative
-- concern, the same reasoning V2's own comment gives for why those rows
-- have no OWN/TEAM/DEPARTMENT variant. So every table below skips owner_id
-- entirely and every service method in this module is gated by a single
-- CUSTOM_FIELD:*:ORGANIZATION or CUSTOM_OBJECT:*:ORGANIZATION authority,
-- the same single-scope pattern WebhookSubscription uses for INTEGRATION.
--
-- custom_fields.standard_entity_type / custom_object_id is the same
-- exactly-one-of-two-nullable-FKs polymorphism campaign_members.lead_id /
-- contact_id used in V9 - a field is either attached to a standard entity
-- type (no FK possible, since "ACCOUNT" etc. isn't a row anywhere - it's
-- validated in CustomFieldService against a fixed enum instead) or to a
-- custom_objects row, never both, never neither.
--
-- custom_field_values is a classic EAV table: one row per (field,
-- record) pair, value always stored as text and parsed/validated against
-- custom_fields.field_type at write time in CustomFieldService rather than
-- given a typed column - simplest way to let an admin add a NUMBER or DATE
-- field tomorrow without a schema change today. record_id is intentionally
-- NOT a foreign key: it points at whichever table custom_fields.standard_
-- entity_type/custom_object_id says it should (a standard entity's id, or a
-- custom_object_records id) - the same untyped-polymorphic-reference
-- tradeoff activities.related_to_id made in V4, for the same reason (more
-- than two possible target tables).

create table custom_objects (
    id                uuid primary key default gen_random_uuid(),
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now(),
    created_by        uuid,
    updated_by        uuid,
    version           bigint not null default 0,
    organization_id   uuid not null references organizations (id),
    api_name          varchar(80) not null,
    label             varchar(150) not null,
    plural_label      varchar(150) not null,
    description       varchar(500),
    active            boolean not null default true
);

create index idx_custom_objects_organization_id on custom_objects (organization_id);
create unique index uq_custom_objects_org_api_name on custom_objects (organization_id, api_name);

create table custom_object_records (
    id                 uuid primary key default gen_random_uuid(),
    created_at         timestamptz not null default now(),
    updated_at         timestamptz not null default now(),
    created_by         uuid,
    updated_by         uuid,
    version            bigint not null default 0,
    custom_object_id   uuid not null references custom_objects (id) on delete cascade,
    organization_id    uuid not null references organizations (id),
    name               varchar(300) not null,
    deleted_at         timestamptz
);

create index idx_custom_object_records_custom_object_id on custom_object_records (custom_object_id);
create index idx_custom_object_records_organization_id on custom_object_records (organization_id);

create table custom_fields (
    id                     uuid primary key default gen_random_uuid(),
    created_at             timestamptz not null default now(),
    updated_at             timestamptz not null default now(),
    created_by             uuid,
    updated_by             uuid,
    version                bigint not null default 0,
    organization_id        uuid not null references organizations (id),
    standard_entity_type   varchar(30),
    custom_object_id       uuid references custom_objects (id) on delete cascade,
    api_name               varchar(80) not null,
    label                  varchar(150) not null,
    field_type             varchar(20) not null,
    required               boolean not null default false,
    display_order          integer not null default 0,
    active                 boolean not null default true,
    constraint chk_custom_fields_exactly_one_target
        check (((standard_entity_type is not null)::int + (custom_object_id is not null)::int) = 1)
);

create index idx_custom_fields_organization_id on custom_fields (organization_id);
create unique index uq_custom_fields_std_api_name
    on custom_fields (organization_id, standard_entity_type, api_name) where standard_entity_type is not null;
create unique index uq_custom_fields_obj_api_name
    on custom_fields (organization_id, custom_object_id, api_name) where custom_object_id is not null;

create table custom_field_picklist_values (
    custom_field_id   uuid not null references custom_fields (id) on delete cascade,
    value             varchar(100) not null,
    display_order     integer not null default 0,
    primary key (custom_field_id, value)
);

create table custom_field_values (
    id                 uuid primary key default gen_random_uuid(),
    created_at         timestamptz not null default now(),
    updated_at         timestamptz not null default now(),
    created_by         uuid,
    updated_by         uuid,
    version            bigint not null default 0,
    organization_id    uuid not null references organizations (id),
    custom_field_id    uuid not null references custom_fields (id) on delete cascade,
    record_id          uuid not null,
    value_text         text
);

create index idx_custom_field_values_organization_id on custom_field_values (organization_id);
create index idx_custom_field_values_record_id on custom_field_values (organization_id, record_id);
create unique index uq_custom_field_values_field_record on custom_field_values (custom_field_id, record_id);
