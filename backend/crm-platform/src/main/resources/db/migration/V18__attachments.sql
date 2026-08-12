-- File attachments - the last "attach something to a CRM record"
-- primitive this platform was missing. Every record type gained a way to
-- log a note/task, a support ticket, an email, or a calendar event about
-- itself over V4/V14/V15; a file upload (a signed contract, a screenshot,
-- an invoice PDF) is the same shape of thing but binary, and needed its
-- own module rather than overloading Activity or EmailMessage.
--
-- ATTACHMENT is a genuinely new permission-catalog resource, same as
-- EMAIL_MESSAGE/CALENDAR_EVENT/TEAM before it - seeded and given a module
-- in this one migration, not a V2 gap. It joins the "core CRM resource"
-- group (see RoleService#isCoreCrmResource) with the full CREATE/READ/
-- UPDATE/DELETE/EXPORT/ASSIGN action set across OWN/TEAM/DEPARTMENT/
-- ORGANIZATION scope, owner-scoped exactly like Ticket/EmailMessage/
-- CalendarEvent - whoever uploaded a file "owns" it the same way a rep
-- owns a ticket they opened. IMPORT is skipped, same reasoning
-- EMAIL_MESSAGE/CALENDAR_EVENT skipped it (V15) - bulk-CSV-importing file
-- uploads isn't a real workflow.
--
-- Unlike calendar_events' optional related_to, related_to_type/
-- related_to_id are required here (not null), same as email_messages' -
-- there's no "standalone" file upload use case in this platform; every
-- attachment is about some Account/Contact/Opportunity/Lead/Ticket. No DB
-- foreign key, same reasoning as every other polymorphic related_to column
-- in this schema - AttachmentService validates it in code.
--
-- storage_key is an opaque pointer into whatever FileStorageService
-- implementation is active (see attachment/storage/FileStorageService's
-- javadoc) - never exposed to API clients, who only ever see file_name/
-- content_type/file_size_bytes and hit GET /{id}/download to get the
-- actual bytes streamed back. The file's bytes themselves are never
-- stored in Postgres - same reasoning nothing else in this schema stores
-- large binary blobs in a column.
create table attachments (
    id                uuid primary key default gen_random_uuid(),
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now(),
    created_by        uuid,
    updated_by        uuid,
    version           bigint not null default 0,
    organization_id   uuid not null references organizations (id),
    related_to_type   varchar(20) not null,
    related_to_id     uuid not null,
    file_name         varchar(255) not null,
    content_type      varchar(150),
    file_size_bytes   bigint not null,
    storage_key       varchar(500) not null,
    description       varchar(1000),
    owner_id          uuid not null references users (id),
    deleted_at        timestamptz
);

create index idx_attachments_organization_id on attachments (organization_id);
create index idx_attachments_owner_id on attachments (organization_id, owner_id);
create index idx_attachments_related_to on attachments (related_to_type, related_to_id);

insert into permissions (resource, action, scope, description)
select resource, action, scope, initcap(replace(resource, '_', ' ')) || ': ' || initcap(action) || ' (' || initcap(scope) || ' scope)'
from (values ('ATTACHMENT')) as r(resource)
cross join (values ('CREATE'), ('READ'), ('UPDATE'), ('DELETE'), ('EXPORT'), ('ASSIGN')) as a(action)
cross join (values ('OWN'), ('TEAM'), ('DEPARTMENT'), ('ORGANIZATION')) as s(scope);
