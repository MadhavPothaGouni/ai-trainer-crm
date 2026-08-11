-- Automation: a Workflow fires when one of the four core CRM entities
-- (Lead/Contact/Account/Opportunity - the same set Activity's
-- relatedToType already covers) is created/updated/deleted, and - for now,
-- the only action_type this platform builds - creates a follow-up Activity
-- task related to whichever record triggered it. WORKFLOW was already
-- seeded in V2 at OWN/TEAM/ORGANIZATION scope (no DEPARTMENT, unlike the
-- core CRM resources - see V2's own comment) with CRUD + MANAGE, so unlike
-- the last two modules (Campaign/KnowledgeArticle, custom fields/objects)
-- this one DOES get an owner_id column and IS gated through
-- ScopeAuthorizationService, the same shape as account/contact/lead/
-- opportunity - a workflow is a personally-owned automation rule, not a
-- shared org resource.
--
-- workflows.trigger_resource/trigger_event are matched against the
-- resourceType/event on CrmAuditEvents.RecordCreated/RecordUpdated/
-- RecordDeleted (see WorkflowEngineListener) - the same domain-event bus
-- WebhookDispatchListener and the audit log already consume, so
-- LeadService/ContactService/AccountService/OpportunityService need zero
-- changes to support workflows firing off their existing publishEvent calls.
--
-- workflow_runs is an execution log, one row per time a workflow actually
-- fired - not a soft-deletable business record, so it skips deleted_at
-- entirely; workflow_id cascades on delete (a workflow's history has no
-- meaning once the workflow itself is gone), but resource_id is
-- deliberately not a foreign key for the same reason
-- custom_field_values.record_id and activities.related_to_id aren't - it
-- points at whichever table the parent workflow's trigger_resource names.

create table workflows (
    id                       uuid primary key default gen_random_uuid(),
    created_at               timestamptz not null default now(),
    updated_at               timestamptz not null default now(),
    created_by               uuid,
    updated_by               uuid,
    version                  bigint not null default 0,
    organization_id          uuid not null references organizations (id),
    owner_id                 uuid not null references users (id),
    name                     varchar(200) not null,
    description              varchar(2000),
    trigger_resource         varchar(20) not null,
    trigger_event            varchar(20) not null,
    action_type              varchar(20) not null default 'CREATE_TASK',
    task_subject             varchar(200) not null,
    task_assignee_user_id    uuid references users (id),
    active                   boolean not null default true,
    run_count                integer not null default 0,
    last_run_at              timestamptz,
    deleted_at               timestamptz
);

create index idx_workflows_organization_id on workflows (organization_id);

-- What WorkflowEngineListener actually queries by on every matching CRM event -
-- keep this fast, since it runs on the hot path of every Lead/Contact/Account/
-- Opportunity create/update/delete across every organization.
create index idx_workflows_trigger on workflows (organization_id, trigger_resource, trigger_event)
    where active = true and deleted_at is null;

create table workflow_runs (
    id                     uuid primary key default gen_random_uuid(),
    created_at             timestamptz not null default now(),
    updated_at             timestamptz not null default now(),
    created_by             uuid,
    updated_by             uuid,
    version                bigint not null default 0,
    workflow_id            uuid not null references workflows (id) on delete cascade,
    organization_id        uuid not null references organizations (id),
    resource_id            uuid not null,
    created_activity_id    uuid,
    status                 varchar(20) not null,
    error_message          varchar(500)
);

create index idx_workflow_runs_workflow_id on workflow_runs (workflow_id, created_at desc);
