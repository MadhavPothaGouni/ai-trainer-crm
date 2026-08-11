-- Email logging and calendar scheduling, tied to a CRM record the same way
-- Activity is. This is genuinely new scope, not a permission-catalog gap
-- like Ticket was - V2 seeded nothing for EMAIL_MESSAGE or CALENDAR_EVENT,
-- so this migration both adds the two new resource rows to the catalog AND
-- ships their tables in the same file (every other module split "seed the
-- permissions" and "create the tables" across V2 + its own migration only
-- because V2 pre-seeded every resource up front; there was no such row here
-- to split from).
--
-- Why this isn't just "use Activity": Activity.Type already has EMAIL and
-- MEETING values and can log "an email happened" or "a meeting happened"
-- against a record, but it has no structured fields for what an email or a
-- meeting actually *is* - no from/to addresses, no direction, no start/end
-- time, no attendee list. EmailMessage and CalendarEvent capture that real
-- structured data; they're a deliberate superset of what Activity already
-- does for these two types, not a duplicate of it. (Activity keeps logging
-- EMAIL/MEETING entries too, same as it always has - the two systems aren't
-- linked, exactly like Quote and Order don't reference each other's line
-- items even though both model "a set of priced products.")
--
-- Both new resources join the "core CRM resource" group (OWN/TEAM/
-- DEPARTMENT/ORGANIZATION scopes, CREATE/READ/UPDATE/DELETE/EXPORT/ASSIGN)
-- and RoleService#isCoreCrmResource, same as Ticket - an email a rep sent or
-- a meeting they organized is naturally "owned" by that rep the same way a
-- lead or a ticket is. IMPORT is deliberately not seeded for either: bulk-
-- CSV-importing a sent-email log or a calendar schedule isn't a real
-- workflow the way importing a contact list is, unlike Account/Contact/
-- Lead/Ticket which all got IMPORT alongside EXPORT.
insert into permissions (resource, action, scope, description)
select resource, action, scope, initcap(replace(resource, '_', ' ')) || ': ' || initcap(action) || ' (' || initcap(scope) || ' scope)'
from (values ('EMAIL_MESSAGE'), ('CALENDAR_EVENT')) as r(resource)
cross join (values ('CREATE'), ('READ'), ('UPDATE'), ('DELETE'), ('EXPORT'), ('ASSIGN')) as a(action)
cross join (values ('OWN'), ('TEAM'), ('DEPARTMENT'), ('ORGANIZATION')) as s(scope);

-- Logged email, one row per message (inbound or outbound). Shape mirrors
-- tickets (V14) - owner-scoped, soft-deletable - except related_to_id has no
-- FK, same reasoning as activities.related_to_id (V4): a single column can't
-- target four different tables, so EmailMessageService validates it in code
-- against whichever repository related_to_type names.
--
-- to_addresses/cc_addresses are comma-separated text, not a Postgres array
-- column or a child table - this project already has an established
-- convention for "a handful of freeform strings" (CustomField.picklistValues
-- and the frontend's toTagList/toPicklistValues helpers both split/join
-- comma-separated text rather than using a native array type or a join
-- table), and a handful of email addresses on one message doesn't carry
-- enough independent identity (no per-address status, no per-address FK) to
-- justify a real child table the way calendar_event_attendees below does.
create table email_messages (
    id              uuid primary key default gen_random_uuid(),
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now(),
    created_by      uuid,
    updated_by      uuid,
    version         bigint not null default 0,
    organization_id uuid not null references organizations (id),
    direction       varchar(10) not null,
    subject         varchar(500) not null,
    body            varchar(10000),
    from_address    varchar(255) not null,
    to_addresses    varchar(2000) not null,
    cc_addresses    varchar(2000),
    related_to_type varchar(20) not null,
    related_to_id   uuid not null,
    sent_at         timestamptz not null,
    owner_id        uuid not null references users (id),
    deleted_at      timestamptz
);

create index idx_email_messages_organization_id on email_messages (organization_id);
create index idx_email_messages_owner_id on email_messages (organization_id, owner_id);
create index idx_email_messages_related_to on email_messages (related_to_type, related_to_id);
create index idx_email_messages_sent_at on email_messages (organization_id, sent_at desc);

-- Calendar events. related_to_id/related_to_type are nullable, unlike
-- email_messages' - an internal team meeting or a personal block on a rep's
-- calendar has no CRM record to attach to, whereas every logged email by
-- definition has one (it was sent or received in the context of some
-- account/contact/lead/opportunity/ticket). owner_id is the organizer, same
-- ownership semantics as every other owner-scoped CRM entity.
create table calendar_events (
    id              uuid primary key default gen_random_uuid(),
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now(),
    created_by      uuid,
    updated_by      uuid,
    version         bigint not null default 0,
    organization_id uuid not null references organizations (id),
    title           varchar(300) not null,
    description     varchar(2000),
    location        varchar(255),
    start_at        timestamptz not null,
    end_at          timestamptz not null,
    all_day         boolean not null default false,
    related_to_type varchar(20),
    related_to_id   uuid,
    owner_id        uuid not null references users (id),
    deleted_at      timestamptz,
    constraint chk_calendar_events_end_after_start check (end_at >= start_at)
);

create index idx_calendar_events_organization_id on calendar_events (organization_id);
create index idx_calendar_events_owner_id on calendar_events (organization_id, owner_id);
create index idx_calendar_events_related_to on calendar_events (related_to_type, related_to_id);
create index idx_calendar_events_start_at on calendar_events (organization_id, start_at);

-- Attendees DO get a real child table, unlike email's to/cc addresses -
-- unlike a plain email address, an attendee has independent identity worth
-- querying on (a real user vs. an outside guest) and its own mutable state
-- (response_status), which is exactly CampaignMember's reasoning (V9) for
-- being a table instead of a comma-separated column. Same "exactly one of
-- two possible references" shape as campaign_members.lead_id/contact_id:
-- an attendee is either an internal user (user_id, a real FK) or an
-- external guest (external_email, no account in this system to reference).
create table calendar_event_attendees (
    id                uuid primary key default gen_random_uuid(),
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now(),
    created_by        uuid,
    updated_by        uuid,
    version           bigint not null default 0,
    calendar_event_id uuid not null references calendar_events (id) on delete cascade,
    user_id           uuid references users (id),
    external_email    varchar(255),
    response_status   varchar(20) not null default 'NEEDS_ACTION',
    constraint chk_calendar_event_attendees_exactly_one_target
        check (((user_id is not null)::int + (external_email is not null)::int) = 1)
);

create index idx_calendar_event_attendees_event_id on calendar_event_attendees (calendar_event_id);
create unique index uq_calendar_event_attendees_event_user on calendar_event_attendees (calendar_event_id, user_id) where user_id is not null;
create unique index uq_calendar_event_attendees_event_email on calendar_event_attendees (calendar_event_id, external_email) where external_email is not null;
