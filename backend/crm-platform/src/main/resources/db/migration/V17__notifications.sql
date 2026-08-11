-- In-app notifications - a teammate's personal inbox ("X assigned you a
-- ticket", "Y mentioned you on this lead"), not a fourth entry in the
-- owner-scoped/shared-org-resource family every prior module (V14-V16)
-- extended.
--
-- No permission-catalog rows are seeded here, deliberately, and that's a
-- real design decision worth spelling out rather than an oversight: every
-- other resource's OWN/TEAM/DEPARTMENT/ORGANIZATION scope answers "how far
-- up the org chart can I see *other people's* records" - a manager seeing
-- their team's tickets, an admin seeing every account. That question has no
-- sensible answer for a notification. A notification is not a shared CRM
-- record with configurable visibility; it is one specific person's mail.
-- Nobody's role should ever let them read a teammate's notification feed,
-- the same way no ADMIN:ORGANIZATION-scope permission lets an admin read
-- another user's ProfilePage password field - it's simply not a resource
-- that widens. So NotificationService (see its own class comment) enforces
-- "recipient_user_id must equal the caller" directly in code, with no
-- ScopeAuthorizationService/@PreAuthorize scope-ladder involved at all -
-- every authenticated user can manage exactly their own rows, full stop.
-- This is the third resource-access shape in the codebase, alongside
-- owner-scoped (Ticket, EmailMessage, CalendarEvent - visibility widens
-- with role) and shared-org-resource (Campaign, Team - one fixed scope for
-- everyone): call it self-scoped - visibility never widens, period.
--
-- recipient_user_id is who the notification is for; sender_user_id
-- (nullable) is who triggered it, kept purely for display ("From Priya
-- Patel") - it grants no access of its own, unlike owner_id elsewhere in
-- this schema.
--
-- related_to_type/related_to_id are optional, same shape as
-- calendar_events (V15) - a system-wide announcement or "you were removed
-- from a team" notification has no single CRM record to deep-link to,
-- whereas "you were assigned this ticket" does. No FK, same reasoning as
-- every other polymorphic related_to column in this schema (V4's activities
-- comment has the fullest version) - NotificationService validates it in
-- code against whichever of the five repositories related_to_type names.
--
-- No deleted_at. Every other module soft-deletes because some other scope
-- (a teammate's TEAM/DEPARTMENT/ORGANIZATION view, an audit trail, a report)
-- might still need to see a record after the owner "removes" it. Nothing
-- else can ever see a notification besides its one recipient, so there's no
-- second party a soft-delete would protect - deleting your own inbox item
-- is the same operation as deleting an email from your own inbox, and
-- Postgres just removes the row.
create table notifications (
    id                uuid primary key default gen_random_uuid(),
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now(),
    created_by        uuid,
    updated_by        uuid,
    version           bigint not null default 0,
    organization_id   uuid not null references organizations (id),
    recipient_user_id uuid not null references users (id),
    sender_user_id    uuid references users (id),
    type              varchar(20) not null,
    title             varchar(200) not null,
    body              varchar(2000),
    related_to_type   varchar(20),
    related_to_id     uuid,
    read_at           timestamptz
);

-- The one query this table exists to serve: "my notifications, newest
-- first, optionally unread-only" - read_at is included directly in the
-- index since NotificationRepository's unread-only queries filter on it.
create index idx_notifications_recipient on notifications (organization_id, recipient_user_id, created_at desc);
create index idx_notifications_recipient_unread on notifications (organization_id, recipient_user_id, read_at);
create index idx_notifications_related_to on notifications (related_to_type, related_to_id);
