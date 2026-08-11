-- Webhook subscriptions, dispatched off the same domain-event bus the audit
-- module already listens to (see audit.event.CrmAuditEvents and
-- audit.listener.AuditEventListener) - WebhookDispatchListener is a second,
-- independent @EventListener on those same RecordCreated/RecordUpdated/
-- RecordDeleted/RecordAssigned events, so account/contact/opportunity/
-- lead/activity/product/quote services don't know webhooks exist any more
-- than they know the audit log exists.
--
-- secret is stored in plaintext (unlike api_keys.hashed_secret) - that's
-- deliberate, not an oversight: a webhook secret has to be readable
-- forever so the subscriber can verify the HMAC-SHA256 signature this
-- platform signs every delivery with (see WebhookDispatchListener), the
-- same shape Stripe/GitHub webhook signing secrets use. An API key secret
-- is the opposite: only ever compared, never displayed again after
-- creation, hence the hash there instead of the raw value.
--
-- event_type is nullable: null means "every event type," a non-null value
-- (e.g. "Opportunity_CREATED", matching the audit action strings the
-- listener already builds as resourceType + "_" + verb) subscribes to
-- exactly one. There's no dedicated WEBHOOK permission resource in the
-- catalog - this rides on INTEGRATION, seeded ORGANIZATION-scope-only in
-- V2, since a webhook subscription is inherently an organization-wide
-- concern, not something one rep owns.
create table webhook_subscriptions (
    id                    uuid primary key default gen_random_uuid(),
    created_at            timestamptz not null default now(),
    updated_at            timestamptz not null default now(),
    created_by            uuid,
    updated_by            uuid,
    version               bigint not null default 0,
    organization_id       uuid not null references organizations (id),
    url                   varchar(500) not null,
    event_type            varchar(100),
    secret                varchar(255) not null,
    active                boolean not null default true,
    created_by_user_id    uuid not null references users (id),
    last_triggered_at     timestamptz,
    last_response_status  integer
);

create index idx_webhook_subscriptions_organization_id on webhook_subscriptions (organization_id);
create index idx_webhook_subscriptions_org_event_type on webhook_subscriptions (organization_id, event_type) where active;
